package step.grid.client;

import java.io.File;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import step.grid.TokenWrapper;
import step.grid.bootstrap.ResourceExtractor;
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;
import step.grid.io.OutputMessage;

public class MockedGridClientTest extends AbstractGridClientTest {

	/**
	 * Test the E2E FileManager process for a single file
	 * 
	 * @throws Exception
	 */
	@Test
	public void testFileRegistration() throws Exception {
		getClient(0,10000,10000);
		
		TokenWrapper token = client.getLocalTokenHandle();

		File testFile = ResourceExtractor.extractResource(this.getClass().getClassLoader(), "TestFile.txt");

		// Register a single file
		FileVersionId fileHandle = client.registerFile(testFile).getVersionId();
		
		JsonNode node = new ObjectMapper().createObjectNode().put("file", fileHandle.getFileId()).put("fileVersion", fileHandle.getVersion());
		
		// Call a simple test message handler that reads the content of the transfered file and returns it
		OutputMessage output = client.call(token.getID(), node, TestMessageHandler.class.getName(), null, new HashMap<>(), 100000);
		
		FileVersion registeredFile = client.getRegisteredFile(fileHandle);
		Assert.assertEquals(testFile, registeredFile.getFile());
		
		// Assert the content of the file matches
		Assert.assertEquals("TEST", output.getPayload().get("content").asText());
		
		// Return the token
		client.returnTokenHandle(token.getID());
	}
	
	@Test
	public void testLocalTokens() throws Exception {
		super.testLocalTokens();
	}
	
	protected void getClient(int readOffset, int reserveTimeout, int releaseTimeout) {
		client = new MockedGridClientImpl();
	}

}
