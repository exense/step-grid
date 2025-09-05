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
package step.grid.client;

import java.io.File;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.glassfish.jersey.client.ClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;

import step.grid.AgentRef;
import step.grid.Grid;
import step.grid.Token;
import step.grid.TokenWrapper;
import step.grid.TokenWrapperOwner;
import step.grid.agent.AgentTokenServices;
import step.grid.agent.handler.MessageHandler;
import step.grid.agent.handler.MessageHandlerPool;
import step.grid.agent.tokenpool.AgentTokenWrapper;
import step.grid.agent.tokenpool.TokenReservationSession;
import step.grid.client.security.JwtTokenGenerator;
import step.grid.contextbuilder.ApplicationContextBuilder;
import step.grid.filemanager.FileManagerClient;
import step.grid.filemanager.FileManagerException;
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;
import step.grid.io.InputMessage;
import step.grid.io.OutputMessage;
import step.grid.tokenpool.Interest;

import static step.grid.client.security.JwtTokenGenerator.initializeJwtTokenGenerator;
import static step.grid.client.security.JwtTokenGenerator.withAuthentication;

public abstract class AbstractGridClientImpl implements GridClient {

	private static final Logger logger = LoggerFactory.getLogger(AbstractGridClientImpl.class);

	private final JwtTokenGenerator jwtTokenGenerator;
	protected Client client;
	protected ConcurrentHashMap<String, TokenReservationSession> localTokenSessions = new ConcurrentHashMap<>();
	
	protected ConcurrentHashMap<String, TokenReservation> reservedTokens = new ConcurrentHashMap<>();
	
	protected AgentTokenServices localAgentTokenServices;
	protected MessageHandlerPool localMessageHandlerPool;
	
	public static final String SELECTION_CRITERION_THREAD = "#THREADID#";
	
	protected final GridClientConfiguration gridClientConfiguration;
	
	private final TokenLifecycleStrategy tokenLifecycleStrategy;
	
	private final Grid grid;
	protected ApplicationContextBuilder applicationContextBuilder;

	public AbstractGridClientImpl(GridClientConfiguration gridClientConfiguration,
			TokenLifecycleStrategy tokenLifecycleStrategy, Grid grid) {
		super();
		this.gridClientConfiguration = gridClientConfiguration;
		this.tokenLifecycleStrategy = tokenLifecycleStrategy;
		this.grid = grid;

		jwtTokenGenerator = initializeJwtTokenGenerator(gridClientConfiguration.getSecurity(), "grid client");
		ClientBuilder newBuilder = ClientBuilder.newBuilder();

		if(gridClientConfiguration.isAllowInvalidSslCertificates()) {
			// allow untrusted certificates
			TrustManager[] trustManager = new X509TrustManager[] { new X509TrustManager() {
				
				@Override
				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}
				
				@Override
				public void checkClientTrusted(X509Certificate[] certs, String authType) {
					
				}
				
				@Override
				public void checkServerTrusted(X509Certificate[] certs, String authType) {
					
				}
			}};
			
			SSLContext sslContext;
			try {
				sslContext = SSLContext.getInstance("SSL");
				sslContext.init(null, trustManager, null);
			} catch (NoSuchAlgorithmException | KeyManagementException e) {
				throw new RuntimeException("Error while initalizing SSL context", e);
			}
			newBuilder.sslContext(sslContext);

			// allow SSL common name mismatch
			newBuilder.hostnameVerifier(new javax.net.ssl.HostnameVerifier() {
				public boolean verify(String hostname, javax.net.ssl.SSLSession sslSession) {
					return true;
				}
			});
		}
		
		client = newBuilder.build();

		GridObjectMapperResolver gridObjectMapperResolver = new GridObjectMapperResolver(gridClientConfiguration.getMaxStringLength());
		client.register(gridObjectMapperResolver);
		client.register(JacksonJsonProvider.class);
		
		initLocalAgentServices();
		initLocalMessageHandlerPool();
	}

	private static class TokenReservation {
		
		private TokenWrapper tokenWrapper;
		private boolean hasSession;
		
		public TokenReservation(TokenWrapper tokenWrapper, boolean hasSession) {
			super();
			this.tokenWrapper = tokenWrapper;
			this.hasSession = hasSession;
		}

		public TokenWrapper getTokenWrapper() {
			return tokenWrapper;
		}

		public boolean hasSession() {
			return hasSession;
		}
	}

	protected void initLocalAgentServices() {
		FileManagerClient fileManagerClient = new FileManagerClient() {
			@Override
			public FileVersion requestFileVersion(FileVersionId fileVersionId, boolean cleanableFromClientCache) throws FileManagerException {
				return getRegisteredFile(fileVersionId);
			}
	
			@Override
			public void removeFileVersionFromCache(FileVersionId fileVersionId) {
				unregisterFile(fileVersionId);
			}

			@Override
			public void cleanupCache() {

			}

			@Override
			public void releaseFileVersion(FileVersion fileVersion) {
				releaseFile(fileVersion);
			}

			@Override
			public void close() throws Exception {

			}
		};
		
		localAgentTokenServices = new AgentTokenServices(fileManagerClient);
		applicationContextBuilder = new ApplicationContextBuilder(gridClientConfiguration.getLocalTokenExecutionContextCacheConfiguration());
		localAgentTokenServices.setApplicationContextBuilder(applicationContextBuilder);
	}

	protected void initLocalMessageHandlerPool() {
		localMessageHandlerPool = new MessageHandlerPool(localAgentTokenServices);
	}

	public TokenWrapper getLocalTokenHandle() {
		Token localToken = new Token();
		String tokenId = UUID.randomUUID().toString();
		localToken.setId(tokenId);
		localToken.setAgentid(Grid.LOCAL_AGENT);		
		localToken.setAttributes(new HashMap<String, String>());
		localToken.setSelectionPatterns(new HashMap<String, Interest>());
		TokenWrapper tokenWrapper = new TokenWrapper(localToken, new AgentRef(Grid.LOCAL_AGENT, "localhost", "default"));
		trackTokenReservation(tokenWrapper, true);
		
		// Attach the session to the token so that it can be accessed by client after token selection
		TokenReservationSession tokenReservationSession = new TokenReservationSession();
		tokenWrapper.getToken().attachObject(TokenWrapper.TOKEN_RESERVATION_SESSION, tokenReservationSession);
		// Store the Session to the local map
		localTokenSessions.put(tokenId, tokenReservationSession);
		return tokenWrapper;
	}

	/**
	 * Keep track of the {@link TokenWrapper} that have been reserved (local or remote)
	 */
	protected void trackTokenReservation(TokenWrapper tokenWrapper, boolean hasSession) {
		reservedTokens.put(tokenWrapper.getID(), new TokenReservation(tokenWrapper, hasSession));
	}

	protected boolean isLocal(TokenWrapper tokenWrapper) {
		return tokenWrapper.getToken().isLocal();
	}
	
	@Override
	public TokenWrapper getTokenHandle(Map<String, String> attributes, Map<String, Interest> interests, boolean createSession) throws AgentCommunicationException {
		return getTokenHandle(attributes, interests, createSession, null);
	}
	
	@Override
	public TokenWrapper getTokenHandle(Map<String, String> attributes, Map<String, Interest> interests, boolean createSession, TokenWrapperOwner tokenOwner) throws AgentCommunicationException {
		TokenWrapper tokenWrapper = getToken(attributes, interests, tokenOwner);
		trackTokenReservation(tokenWrapper, createSession);
		
		if(createSession) {
			try {
				reserveSession(tokenWrapper.getAgent(), tokenWrapper.getToken());			
			} catch(AgentCommunicationException e) {
				tokenLifecycleStrategy.afterTokenReservationError(getTokenLifecycleCallback(tokenWrapper), tokenWrapper, e);
				logger.warn("Error while reserving session for token "+tokenWrapper.getID() +". Returning token to pool. "
						+ "Subsequent call to this token may fail or leaks may appear on the agent side.", e);
				try {
					returnTokenHandle(tokenWrapper.getID());
				} catch (GridClientException e1) {
					logger.warn("Error while returning token "+tokenWrapper.getID()+" to the pool", e1);
				}
				throw e;
			}
		}
		return tokenWrapper;
	}
	
	@Override
	public void returnTokenHandle(String tokenId) throws GridClientException, AgentCommunicationException {
		TokenReservation tokenReservation = reservedTokens.remove(tokenId);
		if(tokenReservation == null) {
			throw new GridClientException("The token with id "+tokenId+" isn't reserved. Please ensure that you're always call getTokenHandle() or getLocalTokenHandle() before calling the call() function.");
		}
		
		TokenWrapper tokenWrapper = tokenReservation.getTokenWrapper();
		try {
			if(tokenReservation.hasSession()) {
				//tokenWrapper.setHasSession(false);
				if(isLocal(tokenWrapper)) {
					// Remove the Session from the local store and close it
					TokenReservationSession session = localTokenSessions.remove(tokenId);
					session.close();
				} else {
					releaseSession(tokenWrapper.getAgent(),tokenWrapper.getToken());			
				}
			}			
		} catch(Exception e) {
			tokenLifecycleStrategy.afterTokenReleaseError(getTokenLifecycleCallback(tokenWrapper), tokenWrapper, e);
			throw e;
		} finally {
			if(!isLocal(tokenWrapper)) {
				grid.returnToken(tokenId);		
			}			
		}
	}

	public void pingAgent(AgentRef agentRef) throws AgentCommunicationException {
		call(agentRef,  "/running", builder -> builder.get(),
				response -> null, gridClientConfiguration.getTokenExecutionInterruptionTimeout());
	}

	@Override
	public OutputMessage call(String tokenId, JsonNode argument, String handler, FileVersionId handlerPackage, Map<String,String> properties, int callTimeout) throws GridClientException, AgentCommunicationException, Exception {
		TokenReservation tokenReservation = getTokenReservation(tokenId);

		TokenWrapper tokenWrapper = tokenReservation.getTokenWrapper();
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

	private TokenReservation getTokenReservation(String tokenId) throws GridClientException {
		TokenReservation tokenReservation = reservedTokens.get(tokenId);
		if(tokenReservation == null) {
			throw new GridClientException("The token with id "+ tokenId +" isn't reserved. You might already have released it.");
		}
		return tokenReservation;
	}

	protected TokenLifecycleStrategyCallback getTokenLifecycleCallback(TokenWrapper tokenWrapper) {
		return new TokenLifecycleStrategyCallback(grid, tokenWrapper.getID());
	}

	private OutputMessage callLocalToken(Token token, InputMessage message) throws Exception {
		TokenReservationSession tokenReservationContext = localTokenSessions.get(token.getId());
		if(tokenReservationContext == null) {
			throw new Exception("The local token "+token.getId()+" is invalid or has already been returned to the pool. Please call getLocalTokenHandle() first.");
		}
		
		OutputMessage output;
		AgentTokenWrapper agentTokenWrapper = new AgentTokenWrapper(token);
		agentTokenWrapper.setServices(localAgentTokenServices);
		agentTokenWrapper.setTokenReservationSession(tokenReservationContext);
		
		localAgentTokenServices.getApplicationContextBuilder().resetContext();
		
		MessageHandler h = localMessageHandlerPool.get(message.getHandler());
		output = h.handle(agentTokenWrapper, message);
		return output;
	}

	private void reserveSession(AgentRef agentRef, Token token) throws AgentCommunicationException {
		if (logger.isDebugEnabled()) {
			logger.debug("Reserving token: " + token.getId());
		}
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

	@Override
	public void interruptTokenExecution(String tokenId) throws GridClientException, AgentCommunicationException {
		TokenReservation tokenReservation = getTokenReservation(tokenId);

		TokenWrapper tokenWrapper = tokenReservation.getTokenWrapper();
		Token token = tokenWrapper.getToken();
		AgentRef agent = tokenWrapper.getAgent();

		call(agent, token, "/interrupt-execution", builder -> builder.post(null),
				response -> null, gridClientConfiguration.getTokenExecutionInterruptionTimeout());
	}

	private void releaseSession(AgentRef agentRef, Token token) throws AgentCommunicationException {
		if (logger.isDebugEnabled()) {
			logger.debug("Releasing token: " + token.getId());
		}
		call(agentRef, token, "/release", builder->{
			return builder.get();
		}, gridClientConfiguration.getReleaseSessionTimeout());
	}

	public void shutdownAgent(AgentRef agentRef) throws AgentCommunicationException {
		call(agentRef, "/shutdown", builder -> builder.post(null),
				response -> null, gridClientConfiguration.getTokenExecutionInterruptionTimeout());
	}
	
	private void call(AgentRef agentRef, Token token, String cmd, Function<Builder, Response> f, int callTimeout) throws AgentCommunicationException {
		call(agentRef, token, cmd, f, null, callTimeout);
	}
	
	private Object call(AgentRef agentRef, Token token, String cmd, Function<Builder, Response> f, Function<Response, Object> mapper, int readTimeout) throws AgentCommunicationException {
		return call( agentRef, "/token/" + token.getId() + cmd, f, mapper, readTimeout);
	}

	private Object call(AgentRef agentRef, String path, Function<Builder, Response> f, Function<Response, Object> mapper, int readTimeout) throws AgentCommunicationException {
		String agentUrl;
		if (gridClientConfiguration.isUseLocalAgentUrlIfAvailable() && agentRef.getLocalAgentUrl() != null) {
			agentUrl = agentRef.getLocalAgentUrl();
		} else {
			agentUrl = agentRef.getAgentUrl();
		}
		int connectionTimeout = gridClientConfiguration.getReadTimeoutOffset();

		String requestPath = agentUrl + path;
		Builder builder = withAuthentication(jwtTokenGenerator, client.target(requestPath).request())
				.property(ClientProperties.READ_TIMEOUT, readTimeout)
				.property(ClientProperties.CONNECT_TIMEOUT, connectionTimeout);

		int maxConnectionRetries = gridClientConfiguration.getMaxConnectionRetries();
		long connectionRetryGracePeriod = gridClientConfiguration.getConnectionRetryGracePeriod();
		if (logger.isDebugEnabled()) {
			logger.debug("Calling agent service {} with readTimeout {}, connectTimeout {}, maxConnectionRetries {}, connectionRetryGracePeriod {}",
					requestPath, readTimeout, connectionTimeout, maxConnectionRetries, connectionRetryGracePeriod);
		}
		int retries = 0;
		AgentConnectException lastException;
		while (true) {
			try {
				return performCall(f, mapper, readTimeout, builder);
			} catch (AgentConnectException e) {
				lastException = e;
				if (logger.isDebugEnabled()) {
					logger.debug("An error occurred while trying to connect to " + requestPath, e);
				}
				// Retry in case of AgentConnectException
				if (retries >= maxConnectionRetries) {
					break;
				} else {
					try {
						Thread.sleep(connectionRetryGracePeriod);
					} catch (InterruptedException ex) {
						logger.info("Sleep interrupted while waiting to retry to connect to " + requestPath);
					}
					retries++;
					logger.warn("Retrying connection to " + requestPath + " after a connection error. Attempt " + retries + "/" + maxConnectionRetries);
				}
			}
		}
		throw new AgentCommunicationException("Failed to establish a connection to " + requestPath + " after " + retries + " retries: " + lastException.getMessage(), lastException);
	}

	private static Object performCall(Function<Builder, Response> f, Function<Response, Object> mapper, int readTimeout, Builder builder) throws AgentCommunicationException, AgentConnectException {
		Response response = null;
		try {
			try {
				response = f.apply(builder);
			} catch(ProcessingException e) {
				Throwable cause = e.getCause();
				if(cause!=null) {
					if(cause instanceof SocketTimeoutException) {
						String causeMessage = cause.getMessage();
						if (causeMessage != null && causeMessage.contains("Read timed out")) {
							throw new AgentCallTimeoutException(readTimeout, e);
						}
						if (causeMessage != null && causeMessage.contains("Connect timed out")) {
							// Throw an AgentConnectTimeoutException to trigger a retry
							throw new AgentConnectException(e);
						} else {
							throw new AgentCommunicationException(e);
						}
					} else if (cause instanceof ConnectException) {
						// A ConnectException is thrown when the connection to the socket fails
						// This covers the "Connection refused" case for instance
						// Throw an AgentConnectTimeoutException to trigger a retry
						throw new AgentConnectException(e);
					} else if (cause instanceof UnknownHostException) {
						// This covers the case where the hostname isn't "Connection refused",
						// Throw an AgentConnectTimeoutException to trigger a retry
						throw new AgentConnectException(e);
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

	private TokenWrapper getToken(Map<String, String> attributes, Map<String, Interest> interests, TokenWrapperOwner tokenOwner) {
		TokenWrapper adapterToken = null;
		try {
			adapterToken = grid.selectToken(attributes, interests, gridClientConfiguration.getMatchExistsTimeout(), gridClientConfiguration.getNoMatchExistsTimeout(), tokenOwner);
		} catch (TimeoutException e) {
			StringBuilder interestList = new StringBuilder();
			if(interests!=null) {
				interests.forEach((k,v)->{interestList.append(k+"="+v+" and ");});				
			}
			
			String desc = " selection criteria " + interestList.toString() + " accepting attributes " + attributes;
			throw new RuntimeException("Not able to find any agent token matching " + desc);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		return adapterToken;
	}
	
	@Override
	public void close() {
		client.close();
		try {
			localMessageHandlerPool.close();
		} catch (Exception e) {
			logger.error("Unable to close the localMessageHandlerPool", e);
		}
		applicationContextBuilder.close();
	}

	@Override
	public FileVersion registerFile(File file, boolean cleanable) throws FileManagerException {
		return grid.registerFile(file, cleanable);
	}

	@Override
	public FileVersion registerFile(InputStream inputStream, String fileName, boolean isDirectory, boolean cleanable) throws FileManagerException {
		return grid.registerFile(inputStream, fileName, isDirectory, cleanable);
	}
	
	@Override
	public FileVersion getRegisteredFile(FileVersionId fileVersionId) throws FileManagerException {
		return grid.getRegisteredFile(fileVersionId);
	}

	@Override
	public void releaseFile(FileVersion fileVersion) {
		grid.releaseFile(fileVersion);
	}

	@Override
	public void unregisterFile(FileVersionId fileVersionId) {
		grid.unregisterFile(fileVersionId);
	}


	@Override
	public List<AgentRef> getAgents() {
		return grid.getAgents();
	}

	@Override
	public List<TokenWrapper> getTokens() {
		return grid.getTokens();
	}

	@Override
	public void markTokenAsFailing(String tokenId, String errorMessage, Exception e) {
		grid.markTokenAsFailing(tokenId, errorMessage, e);
	}

	@Override
	public void removeTokenError(String tokenId) {
		grid.removeTokenError(tokenId);
	}

	@Override
	public void startTokenMaintenance(String tokenId) {
		grid.startTokenMaintenance(tokenId);
	}

	@Override
	public void stopTokenMaintenance(String tokenId) {
		grid.stopTokenMaintenance(tokenId);
	}
}
