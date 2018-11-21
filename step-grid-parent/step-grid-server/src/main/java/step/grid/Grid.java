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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;

import step.grid.agent.RegistrationMessage;
import step.grid.filemanager.FileManagerServer;
import step.grid.filemanager.FileProvider;
import step.grid.tokenpool.Identity;
import step.grid.tokenpool.Token;
import step.grid.tokenpool.TokenPool;
import step.grid.tokenpool.TokenRegistry;

public class Grid implements TokenRegistry, GridFileService {

	public static final String LOCAL_AGENT = "local";

	private ExpiringMap<String, AgentRef> agentRefs;
	
	private TokenPool<Identity, TokenWrapper> tokenPool;

	private final Integer port;
	
	private final Integer keepAliveTimeout;
	
	private Server server;
	
	private FileManagerServer fileManager = new FileManagerServer();
	
	public Grid(Integer port) {
		super();
		this.port = port;
		this.keepAliveTimeout = 60000;
	}
	
	public Grid(Integer port, Integer ttl) {
		super();
		this.port = port;
		this.keepAliveTimeout = ttl;
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
		final Grid grid = this;
		
		resourceConfig.register(new AbstractBinder() {	
			@Override
			protected void configure() {
				bind(grid).to(Grid.class);
				bind(fileManager).to(FileProvider.class);
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
	public TokenWrapper selectToken(Identity pretender, long matchTimeout, long noMatchTimeout)
			throws TimeoutException, InterruptedException {
		TokenWrapper tokenWrapper = tokenPool.selectToken(pretender, matchTimeout, noMatchTimeout);
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
	public String registerFile(File file) {
		return fileManager.registerFile(file);
	}

	@Override
	public File getRegisteredFile(String fileHandle) {
		return fileManager.getFile(fileHandle);
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
}
