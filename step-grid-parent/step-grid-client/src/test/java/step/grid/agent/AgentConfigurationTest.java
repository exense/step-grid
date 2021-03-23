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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.exense.commons.io.FileHelper;
import step.grid.GridImpl;
import step.grid.GridImpl.GridImplConfig;
import step.grid.TokenWrapper;
import step.grid.agent.conf.AgentConf;
import step.grid.agent.conf.TokenConf;
import step.grid.agent.conf.TokenGroupConf;
import step.grid.client.AbstractGridClientImpl.AgentCommunicationException;
import step.grid.client.GridClientConfiguration;
import step.grid.client.GridClientException;
import step.grid.client.LocalGridClientImpl;

public class AgentConfigurationTest {

	private GridImpl grid;
	private Agent agent;

	@Before
	public void before() throws Exception {
		File fileManagerFolder = FileHelper.createTempFolder();

		GridImplConfig gridConfig = new GridImplConfig();
		// disable last modification cache
		gridConfig.setFileLastModificationCacheExpireAfter(0);
		grid = new GridImpl(fileManagerFolder, 0, gridConfig);
		grid.start();
	}

	@Test
	public void testMinimalConfiguration() throws Exception {
		AgentConf agentConf = new AgentConf();
		agentConf.setRegistrationPeriod(100);
		agentConf.setGridHost("http://localhost:" + grid.getServerPort());
		agentConf.setTokenGroups(tokenGroupWith1Token());
		
		startAgent(agentConf);
		testGrid();
	}
	
	@Test
	public void testMissingSection() throws Exception {
		AgentConf agentConf = new AgentConf();
		agentConf.setGridHost("http://localhost:" + grid.getServerPort());
		
		ArrayList<TokenGroupConf> tokenGroups = new ArrayList<TokenGroupConf>();
		TokenGroupConf tokenGroupConf = new TokenGroupConf();
		tokenGroups.add(tokenGroupConf);
		agentConf.setTokenGroups(tokenGroups);

		Exception actualException = null;
		try {
			startAgent(agentConf);
		} catch (Exception e) {
			actualException = e;
		}
		assertNotNull(actualException);
		assertEquals("Missing section 'tokenConf' in agent configuration", actualException.getMessage());
	}
	
	@Test
	public void testTokenGroupWithoutAttributes() throws Exception {
		AgentConf agentConf = new AgentConf();
		agentConf.setGridHost("http://localhost:" + grid.getServerPort());
		
		ArrayList<TokenGroupConf> tokenGroups = new ArrayList<TokenGroupConf>();
		TokenGroupConf tokenGroupConf = new TokenGroupConf();
		tokenGroupConf.setCapacity(1);
		TokenConf tokenConf = new TokenConf();
		// Set the attributes, properties and selection params to null
		// This situation is now allowed with the yaml format
		tokenConf.setAttributes(null);
		tokenConf.setProperties(null);
		tokenConf.setSelectionPatterns(null);
		tokenGroupConf.setTokenConf(tokenConf);
		tokenGroups.add(tokenGroupConf);
		
		agentConf.setTokenGroups(tokenGroups);

		startAgent(agentConf);
		testGrid();
	}

	private ArrayList<TokenGroupConf> tokenGroupWith1Token() {
		ArrayList<TokenGroupConf> tokenGroups = new ArrayList<TokenGroupConf>();
		TokenGroupConf tokenGroupConf = new TokenGroupConf();
		tokenGroupConf.setCapacity(1);
		tokenGroupConf.setTokenConf(new TokenConf());
		tokenGroups.add(tokenGroupConf);
		return tokenGroups;
	}

	@Test
	public void testSSL() throws Exception {
		AgentConf agentConf = new AgentConf();
		agentConf.setGridHost("http://localhost:" + grid.getServerPort());
		agentConf.setRegistrationPeriod(100);
		agentConf.setAgentPort(0);
		
		agentConf.setSsl(true);
		File file = FileHelper.getClassLoaderResourceAsFile(this.getClass().getClassLoader(), "testcert.jks");
		agentConf.setKeyStorePath(file.getAbsolutePath());
		agentConf.setKeyStorePassword("123456");
		agentConf.setKeyManagerPassword("123456");
		agentConf.setGridHost("http://localhost:" + grid.getServerPort());
		agentConf.setTokenGroups(tokenGroupWith1Token());

		startAgent(agentConf);
		testGrid();
	}

	@Test
	public void testSSLWrongConfig() throws Exception {
		AgentConf agentConf = new AgentConf();
		agentConf.setGridHost("dummy");
		agentConf.setSsl(true);

		Exception actualException = null;
		try {
			startAgent(agentConf);
		} catch (Exception e) {
			actualException = e;
		}
		assertNotNull(actualException);
		assertEquals("Missing option 'keyStorePath'. This option is mandatory when SSL is enabled.", actualException.getMessage());
	}
	
	@Test
	public void testWrongConfig() throws Exception {
		AgentConf agentConf = new AgentConf();

		Exception actualException = null;
		try {
			startAgent(agentConf);
		} catch (Exception e) {
			actualException = e;
		}
		assertNotNull(actualException);
		assertEquals("Missing option 'gridHost'. This option is mandatory.", actualException.getMessage());
	}
	
	@Test
	public void testWrongAgentHost() throws Exception {
		AgentConf agentConf = new AgentConf();
		agentConf.setRegistrationPeriod(100);
		agentConf.setGridHost("http://localhost:" + grid.getServerPort());
		agentConf.setAgentPort(0);
		agentConf.setAgentHost("invalid");
		agentConf.setTokenGroups(tokenGroupWith1Token());
		
		startAgent(agentConf);
		
		Exception actualException = null;
		try {
			testGrid();
		} catch (Exception e) {
			actualException = e;
		}
		assertNotNull(actualException);
		assertEquals("javax.ws.rs.ProcessingException: java.net.UnknownHostException: invalid", actualException.getMessage());
	}
	
	@Test
	public void testAgentUrl() throws Exception {
		AgentConf agentConf = new AgentConf();
		agentConf.setTokenGroups(tokenGroupWith1Token());
		agentConf.setRegistrationPeriod(100);
		agentConf.setGridHost("http://localhost:" + grid.getServerPort());
		agentConf.setAgentUrl("http://localhost:11111");
		
		startAgent(agentConf);
		testGrid();
	}

	private void startAgent(AgentConf agentConf) throws Exception {
		agent = new Agent(agentConf);
	}

	@After
	public void after() throws Exception {
		grid.stop();
		if (agent != null) {
			agent.close();
		}
	}

	private void testGrid() throws AgentCommunicationException, GridClientException, Exception {
		GridClientConfiguration gridClientConfiguration = new GridClientConfiguration();
		gridClientConfiguration.setAllowInvalidSslCertificates(true);
		gridClientConfiguration.setNoMatchExistsTimeout(2000);
		LocalGridClientImpl client = new LocalGridClientImpl(gridClientConfiguration, grid);

		TokenWrapper token = client.getTokenHandle(new HashMap<>(), new HashMap<>(), false);
		client.call(token.getID(), new ObjectMapper().createObjectNode().put("a", "b"),
				TestTokenHandler.class.getName(), null, null, 1000);
		client.returnTokenHandle(token.getID());
		client.close();
	}
}
