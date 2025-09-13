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

import ch.exense.commons.io.FileHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.After;
import step.grid.GridImpl;
import step.grid.GridImpl.GridImplConfig;
import step.grid.TokenWrapper;
import step.grid.agent.conf.AgentConf;
import step.grid.agent.conf.AgentForkerConfiguration;
import step.grid.client.AbstractGridClientImpl.AgentCommunicationException;
import step.grid.client.GridClient;
import step.grid.client.GridClientException;
import step.grid.filemanager.FileManagerConfiguration;
import step.grid.filemanager.FileManagerImplConfig;
import step.grid.io.AgentErrorCode;
import step.grid.io.OutputMessage;
import step.grid.security.SymmetricSecurityConfiguration;
import step.grid.tokenpool.Interest;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public abstract class AbstractGridTest {

	public static final String GRID_SECRET = "SlILlEGSIvLKy3JWg7ny8aYoe2D9LNNafQ6OJFKFF8c=";
	protected Agent agent;
	
	protected GridImpl grid;
	protected boolean secureGrid = false;

	protected GridClient client;
	
	protected int nTokens = 1;

	public AbstractGridTest() {
		super();
	}

	public void init() throws Exception {
		init(null);
	}

	public void init(AgentForkerConfiguration agentForkerConfiguration) throws Exception {
		File fileManagerFolder = FileHelper.createTempFolder();
		
		GridImplConfig gridConfig = new GridImplConfig();
		if (secureGrid) {
			SymmetricSecurityConfiguration securityConfiguration = new SymmetricSecurityConfiguration(GRID_SECRET);
			gridConfig.setSecurity(securityConfiguration);
		}
		// disable last modification cache
		FileManagerImplConfig fileManagerImplConfig = new FileManagerImplConfig();
		fileManagerImplConfig.setFileLastModificationCacheExpireAfter(0);
		gridConfig.setFileManagerImplConfig(fileManagerImplConfig);
		grid = new GridImpl(fileManagerFolder, 0, gridConfig);
		grid.start();
				
		AgentConf agentConf = new AgentConf("http://localhost:"+grid.getServerPort(), 0, null, 100);
		if(agentForkerConfiguration != null) {
			agentConf.setAgentForker(agentForkerConfiguration);
		}
		agentConf.setGracefulShutdownTimeout(100l);
		configureAgent(agentConf);
		agentConf.setFileManagerConfiguration(new FileManagerConfiguration());
		if (secureGrid) {
			agentConf.setGridSecurity(new SymmetricSecurityConfiguration(GRID_SECRET));
		}

		agent = new Agent(agentConf);
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put("att1", "val1");
		agent.addTokens(nTokens, attributes, null, null);
	}

	protected void configureAgent(AgentConf agentConf) {

	}
	
	protected void addToken(int count, Map<String, String> attributes) {
		agent.addTokens(count, attributes, null, null);
	}
	
	protected void addToken(int count, Map<String, String> attributes, Map<String, String> properties) {
		agent.addTokens(count, attributes, null, properties);
		try {
			Thread.sleep(150);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	@After
	public void tearDown() throws Exception {
		if (agent != null) {
			agent.close();
		}
		if (grid != null) {
			grid.stop();
		}
		if (client != null) {
			client.close();
		}
	}
	
	protected ObjectNode newDummyJson() {
		return new ObjectMapper().createObjectNode().put("a", "b");
	}
	
	protected void stopTokenMaintenance(TokenWrapper token) {
		grid.stopTokenMaintenance(token.getID());
	}

	protected void startTokenMaintenance(TokenWrapper token) {
		grid.startTokenMaintenance(token.getID());
	}

	protected void removeTokenError(TokenWrapper token) {
		grid.removeTokenError(token.getID());
	}

	protected void returnToken(TokenWrapper token) throws AgentCommunicationException, GridClientException {
		client.returnTokenHandle(token.getID());
	}

	protected OutputMessage callTokenAndProduceAgentError(TokenWrapper token) throws Exception {
		JsonNode o = new ObjectMapper().createObjectNode().put("agentError", AgentErrorCode.TIMEOUT_REQUEST_NOT_INTERRUPTED.toString());
		return client.call(token.getID(), o, TestTokenHandler.class.getName(), null, null, 1000);
	}
	
	protected OutputMessage callToken(TokenWrapper token) throws Exception {
		JsonNode o = newDummyJson();
		return client.call(token.getID(), o, TestTokenHandler.class.getName(), null, null, 1000);
	}

	protected TokenWrapper selectToken() throws AgentCommunicationException {
		return selectToken(true);
	}
	
	protected TokenWrapper selectToken(boolean createSession) throws AgentCommunicationException {
		Map<String, Interest> interests = new HashMap<>();
		interests.put("att1", new Interest(Pattern.compile("val.*"), true));
		TokenWrapper token = client.getTokenHandle(null, interests, createSession);
		return token;
	}
}
