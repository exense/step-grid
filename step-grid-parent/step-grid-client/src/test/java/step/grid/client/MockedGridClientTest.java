package step.grid.client;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.exense.commons.io.FileHelper;
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
	public void testStreamRegistration() throws Exception {
		getClient(0,10000,10000);
		
		TokenWrapper token = client.getLocalTokenHandle();

		// Register a single file
		FileVersionId fileHandle = client
				.registerFile(this.getClass().getClassLoader().getResourceAsStream("TestFile.txt"), "TestFile.txt", false).getVersionId();
		
		JsonNode node = new ObjectMapper().createObjectNode().put("file", fileHandle.getFileId()).put("fileVersion", fileHandle.getVersion());
		
		// Call a simple test message handler that reads the content of the transfered file and returns it
		OutputMessage output = client.call(token.getID(), node, TestMessageHandler.class.getName(), null, new HashMap<>(), 100000);
		
		FileVersion registeredFile = client.getRegisteredFile(fileHandle);
		
		Assert.assertNotNull(registeredFile.getFile());
		String actualContent = FileHelper.readStream(new FileInputStream(registeredFile.getFile()));
		String expectedContent = FileHelper.readClassLoaderResource(this.getClass().getClassLoader(), "TestFile.txt");
		Assert.assertEquals(expectedContent, actualContent);
		
		// Assert the content of the file matches
		Assert.assertEquals("TEST", output.getPayload().get("content").asText());
		
		// Return the token
		client.returnTokenHandle(token.getID());
		
		// Register again
		FileVersionId fileHandle2 = client
				.registerFile(this.getClass().getClassLoader().getResourceAsStream("TestFile.txt"), "TestFile.txt", false).getVersionId();
		Assert.assertEquals(fileHandle, fileHandle2);
		
		// Unregister the stream
		client.unregisterFile(fileHandle);
		// Register it againg
		FileVersionId fileHandle3 = client
				.registerFile(this.getClass().getClassLoader().getResourceAsStream("TestFile.txt"), "TestFile.txt", false).getVersionId();
		// The new handle should be different
		Assert.assertNotSame(fileHandle2, fileHandle3);
		// But the content remains the same
		FileVersion registeredFile3 = client.getRegisteredFile(fileHandle3);
		actualContent = FileHelper.readStream(new FileInputStream(registeredFile3.getFile()));
		Assert.assertEquals(expectedContent, actualContent);
		
	}
	
	@Test
	public void testLocalTokens() throws Exception {
		super.testLocalTokens();
	}
	
	protected void getClient(int readOffset, int reserveTimeout, int releaseTimeout) {
		client = new MockedGridClientImpl();
	}

}
