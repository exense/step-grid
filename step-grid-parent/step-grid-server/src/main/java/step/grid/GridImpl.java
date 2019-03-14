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
package step.grid;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;

import step.commons.helpers.FileHelper;
import step.grid.agent.RegistrationMessage;
import step.grid.filemanager.FileManager;
import step.grid.filemanager.FileManagerException;
import step.grid.filemanager.FileManagerImpl;
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;
import step.grid.tokenpool.Identity;
import step.grid.tokenpool.Interest;
import step.grid.tokenpool.Token;
import step.grid.tokenpool.TokenPool;

public class GridImpl implements Grid {

	private ExpiringMap<String, AgentRef> agentRefs;
	
	private TokenPool<Identity, TokenWrapper> tokenPool;

	private final Integer port;
	
	private final Integer keepAliveTimeout;
	
	private Server server;
	
	private final FileManager fileManager;
	
	public GridImpl(Integer port) {
		this(FileHelper.createTempFolder("filemanager"), port, 60000);
	}
	
	public GridImpl(File fileManagerFolder, Integer port) {
		this(fileManagerFolder, port, 60000);
	}
	
	public GridImpl(File fileManagerFolder, Integer port, Integer ttl) {
		super();
		this.port = port;
		this.keepAliveTimeout = ttl;
		this.fileManager = new FileManagerImpl(fileManagerFolder);
	}

	public void stop() throws Exception {
		server.stop();
		agentRefs.close();
		tokenPool.close();
		
	}

	public void start() throws Exception {
		initializeAgentRefs();
		initializeTokenPool();
		initializeServer();
		startServer();
	}

	private void initializeAgentRefs() {
		agentRefs = new ExpiringMap<>(keepAliveTimeout);
	}
	
	private void initializeTokenPool() {
		tokenPool = new TokenPool<>(new TokenWrapperAffinityEvaluatorImpl());
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
		AgentRef agentRef = message.getAgentRef();
		agentRefs.putOrTouch(agentRef.getAgentId(), agentRef);
		for (step.grid.Token token : message.getTokens()) {
			tokenPool.offerToken(new TokenWrapper(token, agentRef));			
		}	
	}
	
	@Override
	public TokenWrapper selectToken(Map<String, String> attributes, Map<String, Interest> interests, long matchTimeout, long noMatchTimeout)
			throws TimeoutException, InterruptedException {
		TokenPretender tokenPretender = new TokenPretender(attributes, interests);
		TokenWrapper tokenWrapper = tokenPool.selectToken(tokenPretender, matchTimeout, noMatchTimeout);
		tokenWrapper.setState(TokenWrapperState.IN_USE);
		return tokenWrapper;
	}

	@Override
	public void returnToken(TokenWrapper object) {
		object.performAtomically(()->{
			object.setCurrentOwner(null);
			// Only change the state if it is IN_USE. Other states (like ERROR) are kept unchanged
			if(object.getState()==TokenWrapperState.IN_USE) {
				object.setState(TokenWrapperState.FREE);
			}
		});
		tokenPool.returnToken(object);
	}
	
	@Override
	public void markTokenAsFailing(String tokenId, String errorMessage, Exception e) {
		Token<TokenWrapper> token = tokenPool.getToken(tokenId);
		TokenWrapper tokenWrapper = token.getObject();
		TokenHealth tokenHealth = token.getObject().getTokenHealth();
		tokenWrapper.performAtomically(()->{
			tokenHealth.setErrorMessage(errorMessage);
			tokenHealth.setTokenWrapperOwner(token.getObject().getCurrentOwner());
			tokenHealth.setException(e);
			token.getObject().setState(TokenWrapperState.ERROR);
		});
	}
	
	public void removeTokenError(String tokenId) {
		Token<TokenWrapper> token = tokenPool.getToken(tokenId);
		TokenWrapper tokenWrapper = token.getObject();
		TokenHealth tokenHealth = tokenWrapper.getTokenHealth();
		tokenWrapper.performAtomically(()->{
			if(tokenWrapper.getState().equals(TokenWrapperState.ERROR)) {
				tokenHealth.setErrorMessage(null);
				tokenHealth.setException(null);
				token.getObject().setState(TokenWrapperState.FREE);
			}
		});
	}
	
	public void startTokenMaintenance(String tokenId) {
		Token<TokenWrapper> token = tokenPool.getToken(tokenId);
		TokenWrapper tokenWrapper = token.getObject();
		tokenWrapper.setState(TokenWrapperState.MAINTENANCE_REQUESTED);
		tokenPool.addReturnTokenListener(tokenId, t->t.setState(TokenWrapperState.MAINTENANCE));
	}
	
	public void stopTokenMaintenance(String tokenId) {
		Token<TokenWrapper> token = tokenPool.getToken(tokenId);
		TokenWrapper tokenWrapper = token.getObject();
		tokenWrapper.performAtomically(()->{
			// Only change the state if it is currently in MAINTENANCE. Other states are kept unchanged
			if(tokenWrapper.getState().equals(TokenWrapperState.MAINTENANCE)) {
				token.getObject().setState(TokenWrapperState.FREE);
			}
		});
	}

	@Override
	public List<Token<TokenWrapper>> getTokens() {
		return tokenPool.getTokens();
	}
	
	public Collection<AgentRef> getAgents() {
		return agentRefs.values();
	}
	
	public int getServerPort() {
		return ((ServerConnector)server.getConnectors()[0]).getLocalPort();
	}

	@Override
	public FileVersion registerFile(InputStream inputStream, String fileName, boolean isDirectory) throws FileManagerException {
		return fileManager.registerFileVersion(inputStream, fileName, isDirectory, false);
	}
	
	@Override
	public FileVersion registerFile(File file) throws FileManagerException {
		return fileManager.registerFileVersion(file, false);
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
