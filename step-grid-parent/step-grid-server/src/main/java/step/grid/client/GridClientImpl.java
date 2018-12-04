/*******************************************************************************
 * (C) Copyright 2018 Jerome Comte and Dorian Cransac
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
 *******************************************************************************/
package step.grid.client;

import java.io.File;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.ClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

import step.grid.AgentRef;
import step.grid.Grid;
import step.grid.Token;
import step.grid.TokenWrapper;
import step.grid.agent.AgentTokenServices;
import step.grid.agent.ObjectMapperResolver;
import step.grid.agent.handler.MessageHandler;
import step.grid.agent.handler.MessageHandlerPool;
import step.grid.agent.tokenpool.AgentTokenWrapper;
import step.grid.contextbuilder.ApplicationContextBuilder;
import step.grid.filemanager.FileManagerClient;
import step.grid.filemanager.FileManagerException;
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;
import step.grid.io.InputMessage;
import step.grid.io.OutputMessage;
import step.grid.tokenpool.Identity;
import step.grid.tokenpool.Interest;

public class GridClientImpl implements GridClient {
	
	private static final Logger logger = LoggerFactory.getLogger(GridClientImpl.class);
	
	public static final String SELECTION_CRITERION_THREAD = "#THREADID#";
	
	private final GridClientConfiguration gridClientConfiguration;
	
	private final TokenLifecycleStrategy tokenLifecycleStrategy;
	
	private final Grid grid;
	
	private Client client;

	public GridClientImpl(Grid grid) {
		// use default configuration
		this(new GridClientConfiguration(), grid);
	}
	
	public GridClientImpl(GridClientConfiguration gridClientConfiguration, Grid grid) {
		this(gridClientConfiguration, new DefaultTokenLifecycleStrategy(), grid);
	}
	
	public GridClientImpl(GridClientConfiguration gridClientConfiguration, TokenLifecycleStrategy tokenLifecycleStrategy, Grid grid) {
		super();
		
		this.tokenLifecycleStrategy = tokenLifecycleStrategy;
		
		this.gridClientConfiguration = gridClientConfiguration;
		
		this.grid = grid;
		
		client = ClientBuilder.newClient();
		client.register(ObjectMapperResolver.class);
		client.register(JacksonJsonProvider.class);
		
		initLocalAgentServices();
		initLocalMessageHandlerPool();
	}
	
	protected AgentTokenServices localAgentTokenServices;
	
	protected MessageHandlerPool localMessageHandlerPool;
	
	private void initLocalAgentServices() {
		FileManagerClient fileManagerClient = new FileManagerClient() {
			@Override
			public FileVersion requestFileVersion(FileVersionId fileVersionId) throws FileManagerException {
				return grid.getFileManager().requestFileVersion(fileVersionId);
			}
		};
		
		localAgentTokenServices = new AgentTokenServices(fileManagerClient);
		localAgentTokenServices.setApplicationContextBuilder(new ApplicationContextBuilder());
	}
	
	private void initLocalMessageHandlerPool() {
		localMessageHandlerPool = new MessageHandlerPool(localAgentTokenServices);
	}
	
	@Override
	public TokenWrapper getLocalTokenHandle() {
		Token localToken = new Token();
		localToken.setId(UUID.randomUUID().toString());
		localToken.setAgentid(Grid.LOCAL_AGENT);		
		localToken.setAttributes(new HashMap<String, String>());
		localToken.setSelectionPatterns(new HashMap<String, Interest>());
		TokenWrapper tokenWrapper = new TokenWrapper(localToken, new AgentRef(Grid.LOCAL_AGENT, "localhost", "default"));
		return tokenWrapper;
	}
	
	@Override
	public TokenWrapper getTokenHandle(Map<String, String> attributes, Map<String, Interest> interests, boolean createSession) throws AgentCommunicationException {
		TokenPretender tokenPretender = new TokenPretender(attributes, interests);
		TokenWrapper tokenWrapper = getToken(tokenPretender);
		
		if(createSession) {
			try {
				reserveSession(tokenWrapper.getAgent(), tokenWrapper.getToken());			
				tokenWrapper.setHasSession(true);				
			} catch(AgentCommunicationException e) {
				tokenLifecycleStrategy.afterTokenReservationError(getTokenLifecycleCallback(tokenWrapper), tokenWrapper, e);
				logger.warn("Error while reserving session for token "+tokenWrapper.getID() +". Returning token to pool. "
						+ "Subsequent call to this token may fail or leaks may appear on the agent side.", e);
				returnTokenHandle(tokenWrapper);
				throw e;
			}
		}
		return tokenWrapper;
	}
	
	@Override
	public void returnTokenHandle(TokenWrapper tokenWrapper) throws AgentCommunicationException {
		try {
			if(tokenWrapper.hasSession()) {
				//tokenWrapper.setHasSession(false);
				releaseSession(tokenWrapper.getAgent(),tokenWrapper.getToken());			
			}			
		} catch(Exception e) {
			tokenLifecycleStrategy.afterTokenReleaseError(getTokenLifecycleCallback(tokenWrapper), tokenWrapper, e);
			throw e;
		} finally {
			if(!tokenWrapper.getToken().getAgentid().equals(Grid.LOCAL_AGENT)) {
				grid.returnToken(tokenWrapper);		
			}			
		}
	}
	
	@Override
	public OutputMessage call(TokenWrapper tokenWrapper, JsonNode argument, String handler, FileVersionId handlerPackage, Map<String,String> properties, int callTimeout) throws Exception {
		Token token = tokenWrapper.getToken();
		
		AgentRef agent = tokenWrapper.getAgent();
		
		InputMessage message = new InputMessage();
		message.setPayload(argument);
		message.setHandler(handler);
		message.setHandlerPackage(handlerPackage);
		message.setProperties(properties);
		message.setCallTimeout(callTimeout);
		
		OutputMessage output;
		if(token.isLocal()) {
			output = callLocalToken(token, message);
		} else {
			try {
				output = callAgent(agent, token, message);
				tokenLifecycleStrategy.afterTokenCall(getTokenLifecycleCallback(tokenWrapper), tokenWrapper, output);
			} catch(Exception e) {
				tokenLifecycleStrategy.afterTokenCallError(getTokenLifecycleCallback(tokenWrapper), tokenWrapper, e);
				throw e;
			}
		}
		return output;
	}

	protected TokenLifecycleStrategyCallback getTokenLifecycleCallback(TokenWrapper tokenWrapper) {
		return new TokenLifecycleStrategyCallback(grid, tokenWrapper.getID());
	}

	private OutputMessage callLocalToken(Token token, InputMessage message) throws Exception {
		OutputMessage output;
		AgentTokenWrapper agentTokenWrapper = new AgentTokenWrapper(token);
		agentTokenWrapper.setServices(localAgentTokenServices);
		
		localAgentTokenServices.getApplicationContextBuilder().resetContext();
		
		MessageHandler h = localMessageHandlerPool.get(message.getHandler());
		output = h.handle(agentTokenWrapper, message);
		return output;
	}

	private void reserveSession(AgentRef agentRef, Token token) throws AgentCommunicationException {
		call(agentRef, token, "/reserve", builder-> {
			return builder.get();
		}, gridClientConfiguration.getReserveSessionTimeout());
	}
	
	private OutputMessage callAgent(AgentRef agentRef, Token token, InputMessage message) throws AgentCommunicationException {
		return (OutputMessage) call(agentRef, token, "/process", builder->{
			Entity<InputMessage> entity = Entity.entity(message, MediaType.APPLICATION_JSON);
			return builder.post(entity);
		}, response-> {
			return response.readEntity(OutputMessage.class);
		}, gridClientConfiguration.getReadTimeoutOffset()+message.getCallTimeout());
	}
	
	private void releaseSession(AgentRef agentRef, Token token) throws AgentCommunicationException {
		call(agentRef, token, "/release", builder->{
			return builder.get();
		}, gridClientConfiguration.getReleaseSessionTimeout());
	}
	
	private void call(AgentRef agentRef, Token token, String cmd, Function<Builder, Response> f, int callTimeout) throws AgentCommunicationException {
		call(agentRef, token, cmd, f, null, callTimeout);
	}
	
	private Object call(AgentRef agentRef, Token token, String cmd, Function<Builder, Response> f, Function<Response, Object> mapper, int readTimeout) throws AgentCommunicationException {
		String agentUrl = agentRef.getAgentUrl();
		int connectionTimeout = gridClientConfiguration.getReadTimeoutOffset();

		Builder builder =  client.target(agentUrl + "/token/" + token.getId() + cmd).request()
				.property(ClientProperties.READ_TIMEOUT, readTimeout)
				.property(ClientProperties.CONNECT_TIMEOUT, connectionTimeout);
		
		Response response = null;
		try {
			try {
				response = f.apply(builder);				
			} catch(ProcessingException e) {
				Throwable cause = e.getCause();
				if(cause!=null && cause instanceof SocketTimeoutException) {
					String causeMessage =  cause.getMessage();
					if(causeMessage != null && causeMessage.contains("Read timed out")) {
						throw new AgentCallTimeoutException(readTimeout, e);
					} else {
						throw new AgentCommunicationException(e);
					}
				} else {
					throw new AgentCommunicationException(e);
				}
			} catch(Exception e) {
				throw new AgentCommunicationException(e);
			}
			if(!(response.getStatus()==204||response.getStatus()==200)) {
				String error = response.readEntity(String.class);
				throw new AgentSideException(error);
			} else {
				if(mapper!=null) {
					return mapper.apply(response);
				} else {
					return null;
				}
			}
		} finally {
			if(response!=null) {
				response.close();				
			}
		}
	}
	
	public static class AgentCommunicationException extends Exception {
	
		private static final long serialVersionUID = 4337204149079143691L;

		public AgentCommunicationException(String message, Throwable cause) {
			super(message, cause);
		}

		public AgentCommunicationException(Throwable cause) {
			super(cause);
		}

		public AgentCommunicationException(String message) {
			super(message);
		}
	}
	
	@SuppressWarnings("serial")
	public static class AgentCallTimeoutException extends AgentCommunicationException {

		private final long callTimeout; 
		
		public AgentCallTimeoutException(long callTimeout, String message, Throwable cause) {
			super(message, cause);
			this.callTimeout = callTimeout;
		}

		public AgentCallTimeoutException(long callTimeout, Throwable cause) {
			super(cause);
			this.callTimeout = callTimeout;
		}

		public long getCallTimeout() {
			return callTimeout;
		}
		
	}
	
	@SuppressWarnings("serial")
	public static class AgentSideException extends AgentCommunicationException {

		public AgentSideException(String message) {
			super(message);
		}
	}

	private TokenWrapper getToken(final Identity tokenPretender) {
		TokenWrapper adapterToken = null;
		try {
			addThreadIdInterest(tokenPretender);
			adapterToken = grid.selectToken(tokenPretender, gridClientConfiguration.getMatchExistsTimeout(), gridClientConfiguration.getNoMatchExistsTimeout());
		} catch (TimeoutException e) {
			StringBuilder interestList = new StringBuilder();
			if(tokenPretender.getInterests()!=null) {
				tokenPretender.getInterests().forEach((k,v)->{interestList.append(k+"="+v+" and ");});				
			}
			
			String desc = " selection criteria " + interestList.toString() + " accepting attributes " + tokenPretender.getAttributes();
			throw new RuntimeException("Not able to find any agent token matching " + desc);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		markTokenWithThreadId(adapterToken);
		return adapterToken;
	}

	private void markTokenWithThreadId(TokenWrapper adapterToken) {
		if(adapterToken.getAttributes()!=null) {
			adapterToken.getAttributes().put(SELECTION_CRITERION_THREAD, Long.toString(Thread.currentThread().getId()));			
		}
	}

	private void addThreadIdInterest(final Identity tokenPretender) {
		if(tokenPretender.getInterests()!=null) {
			tokenPretender.getInterests().put(SELECTION_CRITERION_THREAD, new Interest(Pattern.compile("^"+Long.toString(Thread.currentThread().getId())+"$"), false));				
		}
	}

	@Override
	public void close() {
		client.close();
	}

	@Override
	public FileVersion registerFile(File file) throws FileManagerException {
		return grid.getFileManager().registerFileVersion(file, false);
	}

	@Override
	public FileVersion getRegisteredFile(FileVersionId fileVersionId) throws FileManagerException {
		return grid.getFileManager().requestFileVersion(fileVersionId);
	}
}
