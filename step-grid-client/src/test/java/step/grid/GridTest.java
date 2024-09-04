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
package step.grid;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import step.grid.agent.AbstractGridTest;
import step.grid.client.GridClientConfiguration;
import step.grid.client.LocalGridClientImpl;
import step.grid.io.AgentErrorCode;
import step.grid.io.OutputMessage;

public class GridTest extends AbstractGridTest {
	
	@Before
	public void init() throws Exception {
		super.init();
		
		GridClientConfiguration gridClientConfiguration = new GridClientConfiguration();
		gridClientConfiguration.setNoMatchExistsTimeout(2000);
		client = new LocalGridClientImpl(gridClientConfiguration, grid);
		// wait for the agent to connect:
		Thread.sleep(1000);
	}
	
	@Test
	public void testHappyPath() throws Exception {
		TokenWrapper token = selectToken();
		callToken(token);
		
		Assert.assertEquals(token.getState(), TokenWrapperState.IN_USE);
		token.setCurrentOwner(new GenericTokenWrapperOwner());
		
		returnToken(token);
		
		Assert.assertEquals(token.getState(), TokenWrapperState.FREE);
		Assert.assertNull(token.getCurrentOwner());	
	
		TokenWrapper token2 = selectToken();
		Assert.assertEquals(token2, token);
		returnToken(token2);
		
		Assert.assertEquals(token2.getState(), TokenWrapperState.FREE);
	}
	
	@Test
	public void testTokenError() throws Exception {
		TokenWrapper token = selectToken();
		OutputMessage outputMessage = callTokenAndProduceAgentError(token);
		
		Assert.assertEquals(AgentErrorCode.TIMEOUT_REQUEST_NOT_INTERRUPTED, outputMessage.getAgentError().getErrorCode());
		Assert.assertEquals(token.getState(), TokenWrapperState.ERROR);

		returnToken(token);
		removeTokenError(token);
		
		Assert.assertEquals(token.getState(), TokenWrapperState.FREE);
		
		token = selectToken();
		outputMessage = callTokenAndProduceAgentError(token);
		returnToken(token);
		
		Assert.assertEquals(token.getState(), TokenWrapperState.ERROR);
		
		Exception actualException = null;
		try {
			token = selectToken();			
		} catch (Exception e) {
			actualException = e;
		}
		
		Assert.assertNotNull(actualException);
		Assert.assertTrue(actualException.getMessage().contains("Not able to find any agent token"));
		
		removeTokenError(token);
		
		token = selectToken();	
		outputMessage = callToken(token);
		
		Assert.assertEquals(token.getState(), TokenWrapperState.IN_USE);
		
		returnToken(token);
		
		Assert.assertEquals(token.getState(), TokenWrapperState.FREE);
	}
	
	@Test
	public void testMaintenance() throws Exception {
		TokenWrapper token = selectToken();
		OutputMessage outputMessage = callToken(token);
		
		Assert.assertNull(outputMessage.getAgentError());
		Assert.assertEquals(token.getState(), TokenWrapperState.IN_USE);

		startTokenMaintenance(token);
		
		Assert.assertEquals(token.getState(), TokenWrapperState.MAINTENANCE_REQUESTED);
		
		returnToken(token);
		
		Assert.assertEquals(token.getState(), TokenWrapperState.MAINTENANCE);
		
		stopTokenMaintenance(token);
		Assert.assertEquals(token.getState(), TokenWrapperState.FREE);
	}
}
