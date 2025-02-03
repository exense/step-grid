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
package step.grid;

import ch.exense.commons.io.FileHelper;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.agent.RegistrationMessage;
import step.grid.filemanager.*;
import step.grid.filemanager.FileManagerImplConfig;
import step.grid.tokenpool.*;
import step.grid.tokenpool.affinityevaluator.TokenPoolAware;
import step.grid.tokenpool.affinityevaluator.TokenWrapperAffinityEvaluatorImpl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

public class GridImpl implements Grid {

	private final static Logger logger = LoggerFactory.getLogger(GridImpl.class);

	private ExpiringMap<String, AgentRef> agentRefs;
	
	private TokenPool<Identity, TokenWrapper> tokenPool;

	private final Integer port;
	
	private final Integer keepAliveTimeout;
	
	private Server server;
	
	private final FileManager fileManager;
	
	private final GridImplConfig gridConfig;

	private final List<RegistrationCallback<AgentRef>> agentRegistrationCallbacks = new CopyOnWriteArrayList<>();

	private boolean acceptRegistrationMessages = false;
	
	public GridImpl(Integer port) throws IOException {
		this(FileHelper.createTempFolder("filemanager"), port);
	}
	
	public GridImpl(File fileManagerFolder, Integer port) {
		this(fileManagerFolder, port, new GridImplConfig());
	}
	
	public GridImpl(File fileManagerFolder, Integer port, GridImplConfig gridConfig) {
		super();
		this.port = port;
		this.keepAliveTimeout = gridConfig.getTtl();
		FileManagerImplConfig config = gridConfig.getFileManagerImplConfig();
		this.fileManager = new FileManagerImpl(fileManagerFolder, config);
		this.gridConfig = gridConfig;
		this.acceptRegistrationMessages = !gridConfig.deferAcceptingRegistrationMessages;
	}
	
	public static class GridImplConfig {

		int ttl = 60000;
		
		FileManagerImplConfig fileManagerImplConfig = new FileManagerImplConfig();

		boolean deferAcceptingRegistrationMessages = false;

		String tokenAffinityEvaluatorClass;
		
		Map<String, String> tokenAffinityEvaluatorProperties;
		
		public GridImplConfig() {
			super();
		}

		public GridImplConfig(FileManagerImplConfig fileManagerImplConfig) {
			super();
			this.fileManagerImplConfig = fileManagerImplConfig;
		}
		
		public int getTtl() {
			return ttl;
		}

		public void setTtl(int ttl) {
			this.ttl = ttl;
		}

		public FileManagerImplConfig getFileManagerImplConfig() {
			return fileManagerImplConfig;
		}

		public void setFileManagerImplConfig(FileManagerImplConfig fileManagerImplConfig) {
			this.fileManagerImplConfig = fileManagerImplConfig;
		}

		public String getTokenAffinityEvaluatorClass() {
			return tokenAffinityEvaluatorClass;
		}

		public void setTokenAffinityEvaluatorClass(String tokenAffinityEvaluatorClass) {
			this.tokenAffinityEvaluatorClass = tokenAffinityEvaluatorClass;
		}

		public Map<String, String> getTokenAffinityEvaluatorProperties() {
			return tokenAffinityEvaluatorProperties;
		}

		public void setTokenAffinityEvaluatorProperties(Map<String, String> tokenAffinityEvaluatorProperties) {
			this.tokenAffinityEvaluatorProperties = tokenAffinityEvaluatorProperties;
		}

		public boolean isDeferAcceptingRegistrationMessages() {
			return deferAcceptingRegistrationMessages;
		}

		public void setDeferAcceptingRegistrationMessages(boolean deferAcceptingRegistrationMessages) {
			this.deferAcceptingRegistrationMessages = deferAcceptingRegistrationMessages;
		}
	}

	public void addAgentRegistrationCallback(RegistrationCallback<AgentRef> callback) {
		agentRegistrationCallbacks.add(callback);
	}

	public void removeAgentRegistrationCallback(RegistrationCallback<AgentRef> callback) {
		agentRegistrationCallbacks.remove(callback);
	}

	public void addTokenRegistrationCallback(RegistrationCallback<TokenWrapper> callback) {
		tokenPool.addTokenRegistrationCallback(callback);
	}

	public void removeTokenRegistrationCallback(RegistrationCallback<TokenWrapper> callback) {
		tokenPool.removeTokenRegistrationCallback(callback);
	}

	public void setAcceptRegistrationMessages(boolean acceptRegistrationMessages) {
		this.acceptRegistrationMessages = acceptRegistrationMessages;
	}

	public void cleanupFileManagerCache() {
		fileManager.cleanupCache();
	}

	public void stop() throws Exception {
		server.stop();
		agentRefs.close();
		tokenPool.close();
		fileManager.close();
	}

	public void start() throws Exception {
		initializeAgentRefs();
		initializeTokenPool();
		initializeServer();
		startServer();
	}

	private void initializeAgentRefs() {
		agentRefs = new ExpiringMap<>(keepAliveTimeout);
		agentRefs.setExpiryCallback(this::unregisterAgents);
	}

	private void unregisterAgents(List<AgentRef> expired) {
		if (logger.isDebugEnabled() && agentRegistrationCallbacks.size() > 0 && expired.size() > 0) {
			logger.debug("Unregistering agents with {} callbacks: {}", agentRegistrationCallbacks.size(), expired);
		}
		agentRegistrationCallbacks.forEach(callback -> callback.afterUnregistering(expired));
	}

	private void initializeTokenPool() throws Exception {
		String tokenAffinityEvaluatorClass = gridConfig.getTokenAffinityEvaluatorClass();
		SimpleAffinityEvaluator<Identity, TokenWrapper> tokenAffinityEvaluator;
		if(tokenAffinityEvaluatorClass != null) {
			try {
				@SuppressWarnings("unchecked")
				Class<? extends SimpleAffinityEvaluator<Identity, TokenWrapper>> class_ = (Class<? extends SimpleAffinityEvaluator<Identity, TokenWrapper>>) Class.forName(tokenAffinityEvaluatorClass);
				tokenAffinityEvaluator = class_.newInstance();
			} catch (Exception e) {
				throw new Exception("Error while creating affinity evaluator using class '"+tokenAffinityEvaluatorClass+"'", e);
			}
		} else {
			tokenAffinityEvaluator = new TokenWrapperAffinityEvaluatorImpl(); 
		}
		//CapacityAwareTokenWrapperAffinityEvaluatorImpl affinityEvaluatorImpl = new CapacityAwareTokenWrapperAffinityEvaluatorImpl();
		tokenPool = new TokenPool<>(tokenAffinityEvaluator);
		
		if(tokenAffinityEvaluator instanceof TokenPoolAware) {
			((TokenPoolAware)tokenAffinityEvaluator).setTokenPool(tokenPool);
		}
		
		// Parse and set token affinity evaluator properties
		tokenAffinityEvaluator.setProperties(gridConfig.getTokenAffinityEvaluatorProperties());
		
		tokenPool.setKeepaliveTimeout(keepAliveTimeout);
	}
	
	private void initializeServer() {
		ResourceConfig resourceConfig = new ResourceConfig();
		resourceConfig.packages(GridServices.class.getPackage().getName());
		resourceConfig.register(JacksonJaxbJsonProvider.class);
		resourceConfig.register(MultiPartFeature.class);
		final GridImpl grid = this;
		
		resourceConfig.register(new AbstractBinder() {	
			@Override
			protected void configure() {
				bind(grid).to(GridImpl.class);
				bind(fileManager).to(FileManager.class);
			}
		});
		ServletContainer servletContainer = new ServletContainer(resourceConfig);
				
		ServletHolder sh = new ServletHolder(servletContainer);
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		context.addServlet(sh, "/*");

		server = new Server(port);
		
		ContextHandlerCollection contexts = new ContextHandlerCollection();
        contexts.setHandlers(new Handler[] { context});
		server.setHandler(contexts);
	}
	
	private void startServer() throws Exception {
		server.start();
	}
	
	protected void handleRegistrationMessage(RegistrationMessage message) {
		if (!acceptRegistrationMessages) {
			if (logger.isDebugEnabled()) {
				logger.debug("Currently not accepting registration messages, ignoring.");
			}
			return;
		}

		AgentRef agentRef = message.getAgentRef();
		boolean allowed = agentRegistrationCallbacks.stream().allMatch(cb -> cb.beforeRegistering(agentRef));
		if (logger.isTraceEnabled()) {
			if (!allowed) {
				logger.trace("One or more callbacks vetoed agent registration, ignoring agent: {}", agentRef);
			} else {
				logger.trace("Allowing agent: {}", agentRef);
			}
		}
		if (allowed) {
			agentRefs.putOrTouch(agentRef.getAgentId(), agentRef);
			for (Token token : message.getTokens()) {
				tokenPool.offerToken(new TokenWrapper(token, agentRef));
			}
		} else {
			if (agentRefs.remove(agentRef.getAgentId()) != null) {
				unregisterAgents(List.of(agentRef));
				for (Token token : message.getTokens()) {
					tokenPool.invalidateToken(new TokenWrapper(token, agentRef));
				}
			}
		}
	}
	
	@Override
	public TokenWrapper selectToken(Map<String, String> attributes, Map<String, Interest> interests, long matchTimeout, long noMatchTimeout, TokenWrapperOwner tokenOwner)
			throws TimeoutException, InterruptedException {
		TokenPretender tokenPretender = new TokenPretender(attributes, interests);
		TokenWrapper tokenWrapper = tokenPool.selectToken(tokenPretender, matchTimeout, noMatchTimeout);
		tokenWrapper.setState(TokenWrapperState.IN_USE);
		tokenWrapper.setCurrentOwner(tokenOwner);
		return tokenWrapper;
	}

	@Override
	public void returnToken(String tokenId) {
		TokenWrapper tokenWrapper = tokenPool.getToken(tokenId);
		// The token might have been removed from the pool => ignore non existing tokens
		if(tokenWrapper != null) {
			tokenWrapper.performAtomically(()->{
				tokenWrapper.setCurrentOwner(null);
				// Only change the state if it is IN_USE. Other states (like ERROR) are kept unchanged
				if(tokenWrapper.getState()==TokenWrapperState.IN_USE) {
					tokenWrapper.setState(TokenWrapperState.FREE);
				}
			});
			tokenPool.returnToken(tokenWrapper);
		}
	}
	
	@Override
	public void markTokenAsFailing(String tokenId, String errorMessage, Exception e) {
		TokenWrapper tokenWrapper = tokenPool.getToken(tokenId);
		TokenHealth tokenHealth = tokenWrapper.getTokenHealth();
		tokenWrapper.performAtomically(()->{
			tokenHealth.setErrorMessage(errorMessage);
			tokenHealth.setTokenWrapperOwner(tokenWrapper.getCurrentOwner());
			tokenHealth.setException(e);
			tokenWrapper.setState(TokenWrapperState.ERROR);
		});
	}
	
	@Override
	public void removeTokenError(String tokenId) {
		TokenWrapper tokenWrapper = tokenPool.getToken(tokenId);
		TokenHealth tokenHealth = tokenWrapper.getTokenHealth();
		tokenWrapper.performAtomically(()->{
			if(tokenWrapper.getState().equals(TokenWrapperState.ERROR)) {
				tokenHealth.setErrorMessage(null);
				tokenHealth.setException(null);
				tokenWrapper.setState(TokenWrapperState.FREE);
			}
		});
	}
	
	@Override
	public void startTokenMaintenance(String tokenId) {
		TokenWrapper tokenWrapper = tokenPool.getToken(tokenId);
		tokenWrapper.setState(TokenWrapperState.MAINTENANCE_REQUESTED);
		tokenPool.addReturnTokenListener(tokenId, t->t.setState(TokenWrapperState.MAINTENANCE));
	}
	
	@Override
	public void stopTokenMaintenance(String tokenId) {
		TokenWrapper tokenWrapper = tokenPool.getToken(tokenId);
		tokenWrapper.performAtomically(()->{
			// Only change the state if it is currently in MAINTENANCE. Other states are kept unchanged
			if(tokenWrapper.getState().equals(TokenWrapperState.MAINTENANCE)) {
				tokenWrapper.setState(TokenWrapperState.FREE);
			}
		});
	}

	@Override
	public void invalidateToken(String tokenId) {
		tokenPool.invalidate(tokenId);
	}

	@Override
	public List<TokenWrapper> getTokens() {
		return tokenPool.getTokens();
	}
	
	public List<AgentRef> getAgents() {
		return new ArrayList<>(agentRefs.values());
	}
	
	public int getServerPort() {
		return ((ServerConnector)server.getConnectors()[0]).getLocalPort();
	}

	@Override
	public FileVersion registerFile(InputStream inputStream, String fileName, boolean isDirectory, boolean cleanable) throws FileManagerException {
		return fileManager.registerFileVersion(inputStream, fileName, isDirectory, false, cleanable);
	}
	
	@Override
	public FileVersion registerFile(File file, boolean cleanable) throws FileManagerException {
		return fileManager.registerFileVersion(file, false, cleanable);
	}

	@Override
	public void releaseFile(FileVersion fileVersion) {
		fileManager.releaseFileVersion(fileVersion);
	}


	@Override
	public FileVersion getRegisteredFile(FileVersionId fileVersionId) throws FileManagerException {
		return fileManager.getFileVersion(fileVersionId);
	}

	@Override
	public void unregisterFile(FileVersionId fileVersionId) {
		fileManager.unregisterFileVersion(fileVersionId);
	}
}
