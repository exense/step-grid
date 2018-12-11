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
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import step.commons.helpers.FileHelper;
import step.grid.TokenWrapper;
import step.grid.agent.AbstractGridTest;
import step.grid.bootstrap.ResourceExtractor;
import step.grid.client.GridClientImpl.AgentCallTimeoutException;
import step.grid.filemanager.FileVersionId;
import step.grid.io.OutputMessage;

public class GridClientTest extends AbstractGridTest {
	
	@Before
	public void init() throws Exception {
		super.init();
	}
	
	/**
	 * Test the E2E FileManager process for a single file
	 * 
	 * @throws Exception
	 */
	@Test
	public void testFileRegistration() throws Exception {
		getClient(0,10000,10000);
		
		TokenWrapper token = selectToken();

		File testFile = ResourceExtractor.extractResource(this.getClass().getClassLoader(), "TestFile.txt");

		// Register a single file
		FileVersionId fileHandle = client.registerFile(testFile).getVersionId();
		
		JsonNode node = new ObjectMapper().createObjectNode().put("file", fileHandle.getFileId()).put("fileVersion", fileHandle.getVersion());
		
		// Call a simple test message handler that reads the content of the transfered file and returns it
		OutputMessage output = client.call(token, node, TestMessageHandler.class.getName(), null, new HashMap<>(), 100000);
		
		// Assert the content of the file matches
		Assert.assertEquals("TEST", output.getPayload().get("content").asText());
		
		// Return the token
		client.returnTokenHandle(token);
	}

	/**
	 * Test the file manager with multiple versions of a single file
	 * 
	 * @throws Exception
	 */
	@Test
	public void testMultipleVersionRegistration() throws Exception {
		getClient(0,10000,10000);
		
		TokenWrapper token = selectToken();

		// Create a simple directory structure
		File tempDir = FileHelper.createTempFolder();

		File file = new File(tempDir+"/File1");
		file.createNewFile();
		
		writeFile(file, "V1");
		
		// Register the directory
		FileVersionId fileHandleVersion1 = client.registerFile(file).getVersionId();
		
		writeFile(file, "V2");
		FileVersionId fileHandleVersion2 = client.registerFile(file).getVersionId();
		
		JsonNode node = new ObjectMapper().createObjectNode().put("file", fileHandleVersion1.getFileId()).put("fileVersion", fileHandleVersion1.getVersion());
		OutputMessage output = client.call(token, node, TestMessageHandler.class.getName(), null, new HashMap<>(), 100000);
		Assert.assertEquals("V1", output.getPayload().get("content").asText());
		
		node = new ObjectMapper().createObjectNode().put("file", fileHandleVersion2.getFileId()).put("fileVersion", fileHandleVersion2.getVersion());
		output = client.call(token, node, TestMessageHandler.class.getName(), null, new HashMap<>(), 100000);
		Assert.assertEquals("V2", output.getPayload().get("content").asText());
		
		// Return the token
		client.returnTokenHandle(token);
	}

	private void writeFile(File file, String content) throws IOException {
		// Sleep 100ms to ensure that the file lastmodification's date get updated
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {}
		try(FileWriter writer = new FileWriter(file)) {
			writer.write(content);
		}
	}

	
	/**
	 * Test the E2E FileManager process for a directory
	 * 
	 * @throws Exception
	 */
	@Test
	public void testFolderRegistration() throws Exception {
		getClient(0,10000,10000);
		
		TokenWrapper token = selectToken();

		// Create a simple directory structure
		File tempDir = FileHelper.createTempFolder();
		File subFolder = new File(tempDir+"/SubFolder1");
		subFolder.mkdirs();
		File file = new File(subFolder+"/File1");
		file.createNewFile();
		File file2 = new File(tempDir+"/File1");
		file2.createNewFile();
		
		// Register the directory
		FileVersionId fileHandle = client.registerFile(tempDir).getVersionId();
		
		JsonNode node = new ObjectMapper().createObjectNode().put("folder", fileHandle.getFileId()).put("fileVersion", fileHandle.getVersion());
		
		// Call a simple test message handler that reads the structure of the transfered folder and returns it
		OutputMessage output = client.call(token, node, TestMessageHandler.class.getName(), null, new HashMap<>(), 100000);
		
		// Return the token
		client.returnTokenHandle(token);
		
		Assert.assertEquals("File1;SubFolder1;", output.getPayload().get("content").asText());
	}
	
	// AgentCallTimeout during reservation is currently impossible to test as we don't have any hook in the reservation where to inject a sleep
	
	@Test
	public void testAgentCallTimeoutDuringRelease() throws Exception {
		getClient(0,10000,1);
		
		TokenWrapper token = selectToken();

		
		client.call(token, newDummyJson().put("testAgentCallTimeoutDuringRelease", ""), TestMessageHandler.class.getName(), null, new HashMap<>(), 1000);
		
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
		
		TokenWrapper token = selectToken(false);
		
		Exception actualException = null;
		ObjectNode o = newDummyJson();
		try {
			client.call(token, o, TestMessageHandler.class.getName(), null, null, 1);			
		} catch (Exception e) {
			actualException = e;
		} finally {
			// Token return should still work after an exception
			client.returnTokenHandle(token);
		}
		
		Assert.assertNotNull(actualException);
		Assert.assertTrue(actualException instanceof AgentCallTimeoutException);
	}
	
	@Test
	public void testHappyPathWithoutSession() throws Exception {
		getClient(0, 10000, 10000);
		
		// Select a token without session
		TokenWrapper token = selectToken(false);
		
		JsonNode o = newDummyJson();
		OutputMessage outputMessage = client.call(token, o, TestMessageHandler.class.getName(), null, null, 10000);			
		
		Assert.assertEquals("OK", outputMessage.getPayload().get("Result").asText());
	}
	
	@Test
	public void testHappyPathWithSession() throws Exception {
		getClient(0, 10000, 10000);
		
		// Select a token with session
		TokenWrapper token = selectToken(true);
		
		JsonNode o = new ObjectMapper().createObjectNode().put("key", "myKey").put("value", "myValue");
		client.call(token, o, TestSessionMessageHandler.class.getName(), null, null, 1000);
		
		// the TestSessionMessageHandler reads the key-values provided a input from the Session and return them in the outputMessage 
		OutputMessage outputMessage = client.call(token, o, TestSessionMessageHandler.class.getName(), null, null, 1000);
		Assert.assertEquals("myValue", outputMessage.getPayload().get("myKey").asText());
		
		client.returnTokenHandle(token);
	}
	
	@Test
	public void testLocalTokens() throws Exception {
		getClient(0, 10000, 10000);
		
		TokenWrapper token = client.getLocalTokenHandle();
		JsonNode o = new ObjectMapper().createObjectNode().put("key", "myKey").put("value", "myValue");
		client.call(token, o, TestSessionMessageHandler.class.getName(), null, null, 1);
		OutputMessage outputMessage = client.call(token, o, TestSessionMessageHandler.class.getName(), null, null, 1);
		
		Assert.assertEquals("myValue", outputMessage.getPayload().get("myKey").asText());
		
		
		client.returnTokenHandle(token);

		Exception e = null;
		try {
			outputMessage = client.call(token, o, TestSessionMessageHandler.class.getName(), null, null, 1);
			
		} catch(Exception ex) {
			e = ex;
		}
		
		Assert.assertEquals("The local token "+token.getID()+" is invalid or has already been returned to the pool. Please call getLocalTokenHandle() first.",e.getMessage());
		
		token = client.getLocalTokenHandle();
		
		outputMessage = client.call(token, o, TestSessionMessageHandler.class.getName(), null, null, 1);
		// The Session object should be empty as we retrieved a new token
		Assert.assertEquals("", outputMessage.getPayload().get("myKey").asText());
		
		client.returnTokenHandle(token);
	}

	protected void getClient(int readOffset, int reserveTimeout, int releaseTimeout) {
		GridClientConfiguration gridClientConfiguration = new GridClientConfiguration();
		gridClientConfiguration.setReadTimeoutOffset(readOffset);
		gridClientConfiguration.setReserveSessionTimeout(reserveTimeout);
		gridClientConfiguration.setReleaseSessionTimeout(releaseTimeout);
		client = new GridClientImpl(gridClientConfiguration, grid);
	}
}
