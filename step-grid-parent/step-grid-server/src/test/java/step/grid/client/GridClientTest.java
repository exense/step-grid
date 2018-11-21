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
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import step.commons.helpers.FileHelper;
import step.grid.TokenWrapper;
import step.grid.agent.AbstractGridTest;
import step.grid.bootstrap.ResourceExtractor;
import step.grid.client.GridClientImpl.AgentCallTimeoutException;
import step.grid.io.OutputMessage;

public class GridClientTest extends AbstractGridTest {
	
	@Before
	public void init() throws Exception {
		super.init();
	}
	
	@Test
	public void testFileRegistration() throws Exception {
		getClient(0,10000,10000);
		
		TokenWrapper token = selectToken();

		File testFile = ResourceExtractor.extractResource(this.getClass().getClassLoader(), "TestFile.txt");

		String fileHandle = client.registerFile(testFile);
		
		JsonNode node = new ObjectMapper().createObjectNode().put("file", fileHandle).put("fileVersion", FileHelper.getLastModificationDateRecursive(testFile));
		
		OutputMessage output = client.call(token, node, TestMessageHandler.class.getName(), null, new HashMap<>(), 10000);
		
		Assert.assertEquals("TEST", output.getPayload().get("content").asText());
	}

	// AgentCallTimeout during reservation is currently impossible to test as we don't have any hook in the reservation where to inject a sleep
	
	@Test
	public void testAgentCallTimeoutDuringRelease() throws Exception {
		getClient(0,10000,1);
		
		TokenWrapper token = selectToken();

		
		client.call(token, newDummyJson(), TestMessageHandler.class.getName(), null, new HashMap<>(), 1000);
		
		Exception actualException = null;
		try {
			returnToken(token);
		} catch (Exception e) {
			actualException = e;
		}
		
		Assert.assertNotNull(actualException);
		Assert.assertTrue(actualException instanceof AgentCallTimeoutException);
	}
	
	@Test
	public void testAgentCallTimeoutException() throws Exception {
		getClient(0, 10000, 10000);
		
		TokenWrapper token = selectToken();
		
		Exception actualException = null;
		JsonNode o = newDummyJson();
		try {
			client.call(token, o, TestMessageHandler.class.getName(), null, null, 1);			
		} catch (Exception e) {
			actualException = e;
		}
		
		Assert.assertNotNull(actualException);
		Assert.assertTrue(actualException instanceof AgentCallTimeoutException);
	}

	protected void getClient(int readOffset, int reserveTimeout, int releaseTimeout) {
		GridClientConfiguration gridClientConfiguration = new GridClientConfiguration();
		gridClientConfiguration.setReadTimeoutOffset(readOffset);
		gridClientConfiguration.setReserveSessionTimeout(reserveTimeout);
		gridClientConfiguration.setReleaseSessionTimeout(releaseTimeout);
		client = new GridClientImpl(gridClientConfiguration, grid, grid);
	}
}
