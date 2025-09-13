/*******************************************************************************
 * Copyright (C) 2020, exense GmbH
 *  
 * This file is part of STEP
 *  
 * STEP is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * STEP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *  
 * You should have received a copy of the GNU Affero General Public License
 * along with STEP.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package step.grid.agent;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.Token;
import step.grid.agent.forker.AgentForker;
import step.grid.agent.tokenpool.*;
import step.grid.agent.tokenpool.AgentTokenPool.InvalidTokenIdException;
import step.grid.bootstrap.BootstrapManager;
import step.grid.contextbuilder.ApplicationContextBuilderException;
import step.grid.filemanager.ControllerCallTimeout;
import step.grid.filemanager.FileManagerException;
import step.grid.io.*;
import step.grid.security.Secured;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Singleton
@Path("/")
public class AgentServices extends AbstractGridServices {

	private static final Logger logger = LoggerFactory.getLogger(AgentServices.class);
	public static final String FORKED_AGENT = "ForkedAgent";

	@Inject
	Agent agent;

	ExecutorService executor;

	AgentTokenPool tokenPool;

	BootstrapManager bootstrapManager;

	private AgentForker agentForker;

	public AgentServices() {
		super();
	}

	@PostConstruct
	public void init() {
		tokenPool = agent.getTokenPool();
		bootstrapManager = agent.getBootstrapManager();
		executor = agent.getTokenExecutor();
		agentForker = agent.getAgentForker();
	}

	class ExecutionContext {
		protected Thread t;
	}

	@Secured
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/token/{id}/process")
	public OutputMessage process(@PathParam("id") String tokenId, final InputMessage message) {
		try {
			final AgentTokenWrapper tokenWrapper = tokenPool.getTokenForExecution(tokenId);
			if(tokenWrapper!=null) {
				if(tokenWrapper.isInUse())
					logger.warn("Token with id=" + tokenWrapper.getUid() + " was already in use.");

				tokenWrapper.setInUse(true);

				if (agentForker != null) {
					return processTokenInForkedAgent(tokenWrapper, message);
				} else {
					final ExecutionContext context = new ExecutionContext();

					Future<OutputMessage> future = executor.submit(new Callable<OutputMessage>() {
						@Override
						public OutputMessage call() throws Exception {
							try {
								context.t = Thread.currentThread();
								agent.getAgentTokenServices().getApplicationContextBuilder().resetContext();
								return bootstrapManager.runBootstraped(tokenWrapper, message);
							} catch(ApplicationContextBuilderException e) {
								return handleContextBuilderError(message, e);
							} catch (Exception e) {
								return handleUnexpectedError(message, e);
							} finally {
								tokenWrapper.setInUse(false);
								tokenPool.afterTokenExecution(tokenId);
							}
						}
					});

					try {
						OutputMessage output = future.get(message.getCallTimeout(), TimeUnit.MILLISECONDS);
						return output;
					} catch(TimeoutException e) {
						List<Attachment> attachments = new ArrayList<>();

						int i=0;
						boolean interruptionSucceeded = false;
						while(!interruptionSucceeded && i++<10) {
							interruptionSucceeded = tryInterruption(tokenWrapper, context, attachments);
						}

						future.cancel(true);

						if(!interruptionSucceeded) {
							return newAgentErrorOutput(new AgentError(AgentErrorCode.TIMEOUT_REQUEST_NOT_INTERRUPTED), attachments.toArray(new Attachment[0]));
						} else {
							return newAgentErrorOutput(new AgentError(AgentErrorCode.TIMEOUT_REQUEST_INTERRUPTED), attachments.toArray(new Attachment[0]));
						}
					}
					//} else {
					//	return newErrorOutput("Token " + tokenId + " already in use. The reason might be that a previous request timed out and couldn't be interrupted.");
					//}
				}
			} else {
				return newAgentErrorOutput(new AgentError(AgentErrorCode.TOKEN_NOT_FOUND));
			}
		} catch(InvalidTokenIdException e) {
			return newAgentErrorOutput(new AgentError(AgentErrorCode.TOKEN_NOT_FOUND));
		} catch (Exception e) {
			return handleUnexpectedError(message, e);
		}
	}

	private OutputMessage processTokenInForkedAgent(AgentTokenWrapper tokenWrapper, InputMessage message) throws Exception {
		String tokenId = tokenWrapper.getUid();
		TokenReservationSession tokenReservationSession = tokenWrapper.getTokenReservationSession();
		boolean closeForkedAgentAfterCall;
		AgentForker.ForkedAgent forkedAgent;
		Map<String, String> allProperties = buildAgentAndTokenPropertyMap(tokenWrapper);
		if (tokenReservationSession instanceof UnusableTokenReservationSession) {
			closeForkedAgentAfterCall = true;
			// Start a forked agent without creating a session on it
			forkedAgent = agentForker.startForkedAgent(false, allProperties);
			tokenReservationSession.registerEventListener(forkedAgent::interruptExecution);
		} else {
			closeForkedAgentAfterCall = false;
			forkedAgent = (AgentForker.ForkedAgent) tokenReservationSession.get(FORKED_AGENT);
			if (forkedAgent == null) {
				// Start a forked agent and create a session on it
				forkedAgent = agentForker.startForkedAgent(true, allProperties);
				tokenReservationSession.registerEventListener(forkedAgent::interruptExecution);
				tokenReservationSession.put(FORKED_AGENT, forkedAgent);
			}
		}
		try {
			return forkedAgent.delegateExecution(message);
		} finally {
			if (closeForkedAgentAfterCall) {
				forkedAgent.close();
			}
			tokenWrapper.setInUse(false);
			tokenPool.afterTokenExecution(tokenId);
		}
	}

	private static Map<String, String> buildAgentAndTokenPropertyMap(AgentTokenWrapper tokenWrapper) {
		Map<String, String> allProperties = new HashMap<>();
		if(tokenWrapper.getServices().getAgentProperties() != null) {
			allProperties.putAll(tokenWrapper.getServices().getAgentProperties());
		}
		if(tokenWrapper.getProperties() != null) {
			allProperties.putAll(tokenWrapper.getProperties());
		}
		return allProperties;
	}

	@Secured
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/token/{id}/interrupt-execution")
	public void interruptTokenExecution(@PathParam("id") String tokenId) throws InvalidTokenIdException {
		logger.info("Interrupting token execution for tokenId: " + tokenId);
		final AgentTokenWrapper tokenWrapper = tokenPool.getTokenForExecution(tokenId);
		if (tokenWrapper != null) {
			tokenWrapper.getTokenReservationSession().getEventListeners().forEach(e -> e.onTokenInterruption());
		}
	}

	private boolean tryInterruption(final AgentTokenWrapper tokenWrapper, final ExecutionContext context,
			List<Attachment> attachments) throws InterruptedException {
		if(tokenWrapper.isInUse()) {
			if(context.t!=null) {
				StackTraceElement[] stacktrace = context.t.getStackTrace();
				Attachment stacktraceAttachment = generateAttachmentForStacktrace("stacktrace_before_interruption.log",stacktrace);
				attachments.add(stacktraceAttachment);
				context.t.interrupt();
				Thread.sleep(10); 
				return !tokenWrapper.isInUse();			
			} else {
				return false;
			}
		} else {
			return true;
		}
	}

	@Secured
	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/token/{id}/reserve")
	public void reserveToken(@PathParam("id") String tokenId) throws InvalidTokenIdException {
		if (logger.isDebugEnabled()) {
			logger.debug("Reserving token: " + tokenId);
		}
		tokenPool.createTokenReservationSession(tokenId);
	}


	@Secured
	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/token/{id}/release")
	public void releaseToken(@PathParam("id") String tokenId) throws InvalidTokenIdException {
		if (logger.isDebugEnabled()) {
			logger.debug("Releasing token: " + tokenId);
		}
		tokenPool.closeTokenReservationSession(tokenId);
	}

	protected OutputMessage handleContextBuilderError(InputMessage inputMessage, ApplicationContextBuilderException e) {
		Throwable cause = e.getCause();
		AgentError error;
		if(cause instanceof FileManagerException) {
			FileManagerException fileProviderException = (FileManagerException) cause;
			
			Map<AgentErrorCode.Details, String> details = new HashMap<>();
			details.put(AgentErrorCode.Details.FILE_HANDLE, fileProviderException.getFileVersionId().getFileId());
			details.put(AgentErrorCode.Details.FILE_VERSION, fileProviderException.getFileVersionId().getVersion());
			
			Throwable fileProviderExceptionCause = fileProviderException.getCause();
			if(fileProviderExceptionCause instanceof ControllerCallTimeout) {
				error = new AgentError(AgentErrorCode.CONTEXT_BUILDER_FILE_PROVIDER_CALL_TIMEOUT);
				details.put(AgentErrorCode.Details.TIMEOUT, Long.toString(((ControllerCallTimeout) fileProviderExceptionCause).getTimeout()));
				error.setErrorDetails(details);
			} else {
				error = new AgentError(AgentErrorCode.CONTEXT_BUILDER_FILE_PROVIDER_CALL_ERROR, details);
			}
		} else {
			error = new AgentError(AgentErrorCode.CONTEXT_BUILDER);
		}
		OutputMessage output = newAgentErrorOutput(error);
		output.addAttachment(generateAttachmentForException(e));
		return output;
	}

	@Secured
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/token/list")
	public List<Token> listTokens() {
		return agent.getTokens();
	}

	@Secured
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/shutdown")
	public void shutdown(@Context HttpServletRequest request) {
		logger.info("Received shutdown request from " + request.getRemoteAddr());
		new Thread() {
			@Override
			public void run() {
				try {
					agent.close();
				} catch (Exception e) {
					logger.error("Error while shutting down", e);
				}
			}
		}.start();;
	}

	@Secured
	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/pre-stop")
	public void preStop(@Context HttpServletRequest request) {
		logger.info("Received pre-stop request from " + request.getRemoteAddr());
		try {
			agent.preStop();
		} catch (Exception e) {
			logger.error("Error while pre-stopping", e);
		}
	}


	// For readiness probe
	@GET
	@Path("/registered")
	public Response isRegistered(@Context HttpServletRequest request) {
		logger.debug("Received registered request from " + request.getRemoteAddr());
		if(agent.isRegistered()) {
			return Response.status(Response.Status.OK).entity("Agent is registered").build();
		} else {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Agent is not registered").build();
		}
	}

	// For liveness probe
	@GET
	@Path("/running")
	public Response isRunning(@Context HttpServletRequest request) {
		logger.debug("Received running request from " + request.getRemoteAddr());
		if(agent.isRunning()) {
			return Response.status(Response.Status.OK).entity("Agent is running").build();
		} else {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Agent is not running").build();
		}
	}
}
