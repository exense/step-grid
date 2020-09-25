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

import org.junit.Test;

public class RemoteGridClientTest extends AbstractGridClientTest {
	
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
		client = new RemoteGridClientImpl(gridClientConfiguration, "http://localhost:"+grid.getServerPort());
	}
}
