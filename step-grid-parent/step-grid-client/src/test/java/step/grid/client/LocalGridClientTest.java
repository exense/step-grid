package step.grid.client;

import org.junit.Test;

public class LocalGridClientTest extends AbstractGridClientTest {

	/**
	 * Test the E2E FileManager process for a single file
	 * 
	 * @throws Exception
	 */
	@Test
	public void testFileRegistration() throws Exception {
		super.testFileRegistration();
	}
	

	/**
	 * Test the E2E FileManager process for a directory
	 * 
	 * @throws Exception
	 */
	@Test
	public void testFolderRegistration() throws Exception {
		super.testFolderRegistration();
	}
	
	@Test
	public void testMultipleVersionRegistration() throws Exception {
		super.testMultipleVersionRegistration();
	}	
	
	@Test
	public void testAgentCallTimeoutDuringRelease() throws Exception {
		super.testAgentCallTimeoutDuringRelease();
	}
	      
	@Test
	public void testAgentCallTimeoutException() throws Exception {
		super.testAgentCallTimeoutException();
	}
	
	@Test
	public void testHappyPathWithoutSession() throws Exception {
		super.testHappyPathWithoutSession();
	}
	
	@Test
	public void testHappyPathWithSessionLocalClient() throws Exception {
		super.testHappyPathWithSession();
	}
	
	@Test
	public void testLocalTokens() throws Exception {
		super.testLocalTokens();
	}
	
	protected void getClient(int readOffset, int reserveTimeout, int releaseTimeout) {
		GridClientConfiguration gridClientConfiguration = new GridClientConfiguration();
		gridClientConfiguration.setReadTimeoutOffset(readOffset);
		gridClientConfiguration.setReserveSessionTimeout(reserveTimeout);
		gridClientConfiguration.setReleaseSessionTimeout(releaseTimeout);
		client = new LocalGridClientImpl(gridClientConfiguration, grid);
	}

}
