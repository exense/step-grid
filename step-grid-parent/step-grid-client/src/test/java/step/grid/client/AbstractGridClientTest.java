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

import ch.exense.commons.io.FileHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import step.grid.AgentRef;
import step.grid.TokenWrapper;
import step.grid.TokenWrapperState;
import step.grid.agent.AbstractGridTest;
import step.grid.bootstrap.ResourceExtractor;
import step.grid.client.AbstractGridClientImpl.AgentCallTimeoutException;
import step.grid.client.AbstractGridClientImpl.AgentCommunicationException;
import step.grid.client.reports.GridReportBuilder;
import step.grid.client.reports.TokenGroupCapacity;
import step.grid.filemanager.FileManagerException;
import step.grid.filemanager.FileVersionId;
import step.grid.io.OutputMessage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static step.grid.client.TestMessageHandler.*;

public abstract class AbstractGridClientTest extends AbstractGridTest {
	
	@Before
	public void init() throws Exception {
		super.init();
	}

	protected void testFileRegistration() throws AgentCommunicationException, FileManagerException, Exception {
		getClient(0,10000,10000);
		
		TokenWrapper token = selectToken();

		File testFile = ResourceExtractor.extractResource(this.getClass().getClassLoader(), "TestFile.txt");

		// Register a single file
		FileVersionId fileHandle = client.registerFile(testFile, true).getVersionId();
		
		JsonNode node = new ObjectMapper().createObjectNode().put("file", fileHandle.getFileId()).put("fileVersion", fileHandle.getVersion());
		
		// Call a simple test message handler that reads the content of the transfered file and returns it
		OutputMessage output = client.call(token.getID(), node, TestMessageHandler.class.getName(), null, new HashMap<>(), 100000);
		
		// Assert the content of the file matches
		assertEquals("TEST", output.getPayload().get("content").asText());
		
		// Return the token
		client.returnTokenHandle(token.getID());
	}

	protected void testMultipleVersionRegistration()
			throws AgentCommunicationException, IOException, FileManagerException, Exception {
		getClient(0,10000,10000);
		
		TokenWrapper token = selectToken();

		// Create a simple directory structure
		File tempDir = FileHelper.createTempFolder();

		File file = new File(tempDir+"/File1");
		file.createNewFile();
		
		writeFile(file, "V1");
		
		// Register the directory
		FileVersionId fileHandleVersion1 = client.registerFile(file, true).getVersionId();
		
		writeFile(file, "V2");
		FileVersionId fileHandleVersion2 = client.registerFile(file, true).getVersionId();
		
		JsonNode node = new ObjectMapper().createObjectNode().put("file", fileHandleVersion1.getFileId()).put("fileVersion", fileHandleVersion1.getVersion());
		OutputMessage output = client.call(token.getID(), node, TestMessageHandler.class.getName(), null, new HashMap<>(), 100000);
		assertEquals("V1", output.getPayload().get("content").asText());
		
		node = new ObjectMapper().createObjectNode().put("file", fileHandleVersion2.getFileId()).put("fileVersion", fileHandleVersion2.getVersion());
		output = client.call(token.getID(), node, TestMessageHandler.class.getName(), null, new HashMap<>(), 100000);
		assertEquals("V2", output.getPayload().get("content").asText());
		
		// Return the token
		client.returnTokenHandle(token.getID());
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
	protected void testFolderRegistration() throws Exception {
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
		FileVersionId fileHandle = client.registerFile(tempDir, true).getVersionId();
		
		JsonNode node = new ObjectMapper().createObjectNode().put("folder", fileHandle.getFileId()).put("fileVersion", fileHandle.getVersion());
		
		// Call a simple test message handler that reads the structure of the transfered folder and returns it
		OutputMessage output = client.call(token.getID(), node, TestMessageHandler.class.getName(), null, new HashMap<>(), 100000);
		
		// Return the token
		client.returnTokenHandle(token.getID());

		List<String> content = Arrays.stream(output.getPayload().get("content").asText().split(";")).sorted().collect(Collectors.toList());
		List<String> result = Arrays.stream("SubFolder1;File1;".split(";")).sorted().collect(Collectors.toList());
		assertEquals(result, content);
	}
	
	// AgentCallTimeout during reservation is currently impossible to test as we don't have any hook in the reservation where to inject a sleep
	
	protected void testAgentCallTimeoutDuringRelease() throws Exception {
		getClient(0,10000,1);
		
		TokenWrapper token = selectToken();

		
		client.call(token.getID(), newDummyJson().put("testAgentCallTimeoutDuringRelease", ""), TestMessageHandler.class.getName(), null, new HashMap<>(), 1000);
		
		Exception actualException = null;
		try {
			returnToken(token);
		} catch (Exception e) {
			actualException = e;
		}
		
		Assert.assertNotNull(actualException);
		Assert.assertTrue(actualException instanceof AgentCallTimeoutException);
	}
	      
	protected void testAgentCallTimeoutException() throws Exception {
		getClient(0, 10000, 10000);
		
		TokenWrapper token = selectToken(false);
		
		Exception actualException = null;
		ObjectNode o = newDummyJson();
		try {
			client.call(token.getID(), o, TestMessageHandler.class.getName(), null, null, 1);			
		} catch (Exception e) {
			actualException = e;
		} finally {
			// Token return should still work after an exception
			client.returnTokenHandle(token.getID());
		}
		
		Assert.assertNotNull(actualException);
		Assert.assertTrue(actualException instanceof AgentCallTimeoutException);
	}

	protected void testTokenInterruption() throws Exception {
		getClient(0, 10000, 10000);

		TokenWrapper token = selectToken(false);

		ObjectNode o = newDummyJson();
		o.put(TEST_TOKEN_INTERRUPTION, "");

		// Call the token asynchronously
		CompletableFuture<OutputMessage> future = CompletableFuture.supplyAsync(() -> {
			try {
				return client.call(token.getID(), o, TestMessageHandler.class.getName(), null, null, 5000);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		// Wait 500ms to ensure that the TestMessageHandler has registered the interruption hook
		Thread.sleep(500);
		// Interrupt the token execution
		client.interruptTokenExecution(token.getID());
		OutputMessage outputMessage = future.get();
		client.returnTokenHandle(token.getID());
		// Ensure the hooks has been called
		assertEquals(INTERRUPTED, outputMessage.getPayload().get(RESULT).asText());
	}
	
	protected void testHappyPathWithoutSession() throws Exception {
		getClient(0, 10000, 10000);

		// Select a token without session
		TokenWrapper token = selectToken(false);

		JsonNode o = newDummyJson();
		OutputMessage outputMessage = client.call(token.getID(), o, TestMessageHandler.class.getName(), null, null, 10000);

		client.returnTokenHandle(token.getID());

		assertEquals("OK", outputMessage.getPayload().get("Result").asText());

		// Test getAgents method of grid
		List<AgentRef> agents = grid.getAgents();
		assertEquals(1, agents.size());

		// Test getAgents method of grid client
		agents = client.getAgents();
		assertEquals(1, agents.size());

		// Test getTokens method of grid
		List<TokenWrapper> tokens = grid.getTokens();
		assertEquals(1, tokens.size());

		// Test getTokens method of grid client
		tokens = client.getTokens();
		assertEquals(1, tokens.size());
	}

	protected void testHappyPathWithSession() throws AgentCommunicationException, Exception {
		getClient(0, 10000, 10000);
		
		// Select a token with session
		TokenWrapper token = selectToken(true);
		
		JsonNode o = new ObjectMapper().createObjectNode().put("key", "myKey").put("value", "myValue");
		client.call(token.getID(), o, TestSessionMessageHandler.class.getName(), null, null, 1000);
		
		// the TestSessionMessageHandler reads the key-values provided a input from the Session and return them in the outputMessage 
		OutputMessage outputMessage = client.call(token.getID(), o, TestSessionMessageHandler.class.getName(), null, null, 1000);
		assertEquals("myValue", outputMessage.getPayload().get("myKey").asText());
		
		client.returnTokenHandle(token.getID());
	}
	
	protected void testLocalTokens() throws Exception {
		getClient(0, 10000, 10000);
		
		TokenWrapper token = client.getLocalTokenHandle();
		JsonNode o = new ObjectMapper().createObjectNode().put("key", "myKey").put("value", "myValue");
		client.call(token.getID(), o, TestSessionMessageHandler.class.getName(), null, null, 1);
		OutputMessage outputMessage = client.call(token.getID(), o, TestSessionMessageHandler.class.getName(), null, null, 1);
		
		assertEquals("myValue", outputMessage.getPayload().get("myKey").asText());
		
		
		client.returnTokenHandle(token.getID());

		Exception e = null;
		try {
			client.returnTokenHandle(token.getID());
		} catch(Exception ex) {
			e = ex;
		}
		
		assertEquals(GridClientException.class, e.getClass());
		assertEquals("The token with id "+token.getID()+" isn't reserved. Please ensure that you're always call getTokenHandle() or getLocalTokenHandle() before calling the call() function.",e.getMessage());
		
		e = null;
		try {
			outputMessage = client.call(token.getID(), o, TestSessionMessageHandler.class.getName(), null, null, 1);
		} catch(Exception ex) {
			e = ex;
		}
		
		assertEquals("The token with id "+token.getID()+" isn't reserved. You might already have released it.",e.getMessage());
		
		token = client.getLocalTokenHandle();
		
		outputMessage = client.call(token.getID(), o, TestSessionMessageHandler.class.getName(), null, null, 1);
		// The Session object should be empty as we retrieved a new token
		assertEquals("", outputMessage.getPayload().get("myKey").asText());
		
		client.returnTokenHandle(token.getID());
	}

	protected void testTokenManagement() throws Exception {
		getClient(0, 100, 100);

		// Select a token without session
		TokenWrapper token = selectToken(false);

		assertNotNull(token);

		String tokenId = token.getID();
		client.returnTokenHandle(tokenId);

		client.startTokenMaintenance(tokenId);

		TokenWrapper tokenWrapper = getTokenById(tokenId);
		assertEquals(TokenWrapperState.MAINTENANCE, tokenWrapper.getState());

		client.stopTokenMaintenance(tokenId);

		tokenWrapper = getTokenById(tokenId);
		assertEquals(TokenWrapperState.FREE, tokenWrapper.getState());

		client.markTokenAsFailing(tokenId, "My Error", new Exception());

		tokenWrapper = getTokenById(tokenId);
		assertEquals(TokenWrapperState.ERROR, tokenWrapper.getState());
		assertEquals("My Error", tokenWrapper.getTokenHealth().getErrorMessage());

		client.removeTokenError(tokenId);

		tokenWrapper = getTokenById(tokenId);
		assertEquals(TokenWrapperState.FREE, tokenWrapper.getState());
		assertNull(tokenWrapper.getTokenHealth().getErrorMessage());

		List<AgentRef> agents = client.getAgents();
		assertEquals(1, agents.size());
	}

	protected void testReportBuilder() throws Exception {
		getClient(0,10000,10000);

		TokenWrapper token = selectToken(true);

		GridReportBuilder reportBuilder = new GridReportBuilder(client);

		List<TokenGroupCapacity> usageByIdentity = reportBuilder.getUsageByIdentity(List.of());
		assertEquals(1, usageByIdentity.size());
		assertEquals(1, (int) usageByIdentity.get(0).getCountByState().get(TokenWrapperState.IN_USE));

		usageByIdentity = reportBuilder.getUsageByIdentity(List.of("url"));
		assertEquals(1, usageByIdentity.size());
		assertEquals(1, (int) usageByIdentity.get(0).getCountByState().get(TokenWrapperState.IN_USE));

		returnToken(token);

		usageByIdentity = reportBuilder.getUsageByIdentity(List.of());
		assertEquals(1, usageByIdentity.size());
		assertEquals(1, (int) usageByIdentity.get(0).getCountByState().get(TokenWrapperState.FREE));

		List<TokenWrapper> tokenAssociations = reportBuilder.getTokenAssociations(false);
		assertEquals(1, tokenAssociations.size());

		tokenAssociations = reportBuilder.getTokenAssociations(true);
		assertEquals(0, tokenAssociations.size());

		Set<String> tokenAttributeKeys = reportBuilder.getTokenAttributeKeys();
		assertEquals(Set.of("$agentid", "$agenttype", "$tokenid", "att1"), tokenAttributeKeys);
	}

	private TokenWrapper getTokenById(String tokenId) {
		return client.getTokens().stream().filter(t -> t.getID().equals(tokenId)).findFirst().get();
	}

	protected abstract void getClient(int readOffset, int reserveTimeout, int releaseTimeout);
}
