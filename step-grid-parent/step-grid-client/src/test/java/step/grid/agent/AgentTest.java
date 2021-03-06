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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import step.grid.TokenWrapper;
import step.grid.client.LocalGridClientImpl;
import step.grid.io.AgentErrorCode;
import step.grid.io.OutputMessage;
import step.grid.tokenpool.Interest;

public class AgentTest extends AbstractGridTest {
	
	@Before
	public void init() throws Exception {
		super.init();
		client = new LocalGridClientImpl(grid);
	}
	
	@Test
	public void test() throws Exception {
		Map<String, Interest> interests = new HashMap<>();
		interests.put("att1", new Interest(Pattern.compile("val.*"), true));
		
		JsonNode o = newDummyJson();
		
		TokenWrapper token = client.getTokenHandle(null, interests, true);
		OutputMessage outputMessage = client.call(token.getID(), o, TestTokenHandler.class.getName(), null, null, 1000);
		Assert.assertEquals(outputMessage.getPayload(), o);;
	}
	
	@Test
	public void testException() throws Exception {
		Map<String, Interest> interests = new HashMap<>();
		interests.put("att1", new Interest(Pattern.compile("val.*"), true));
		
		JsonNode o = new ObjectMapper().createObjectNode().put("exception", "My Error");
		
		TokenWrapper token = client.getTokenHandle(null, interests, true);
		OutputMessage outputMessage = client.call(token.getID(), o, TestTokenHandler.class.getName(), null, null, 1000);
		Assert.assertEquals(AgentErrorCode.UNEXPECTED, outputMessage.getAgentError().getErrorCode());
		Assert.assertTrue(outputMessage.getAttachments().size()==1);
	}
	
	@Test
	public void testNoSession() throws Exception {
		Map<String, Interest> interests = new HashMap<>();
		interests.put("att1", new Interest(Pattern.compile("val.*"), true));
		
		JsonNode o = newDummyJson();
		
		TokenWrapper token = client.getTokenHandle(null, interests, false);		
		OutputMessage outputMessage = client.call(token.getID(), o, TestTokenHandler.class.getName(), null, null, 1000);
		Assert.assertEquals(outputMessage.getPayload(), o);;
	}
	
	@Test
	public void testTimeout() throws Exception {		
		JsonNode o = new ObjectMapper().createObjectNode().put("delay", 5000);
		
		TokenWrapper token = client.getTokenHandle(null, null, true);
		OutputMessage outputMessage = client.call(token.getID(), o, TestTokenHandler.class.getName(), null, null, 100);
		
		Assert.assertEquals(AgentErrorCode.TIMEOUT_REQUEST_INTERRUPTED,outputMessage.getAgentError().getErrorCode());
		Assert.assertTrue(outputMessage.getAttachments().get(0).getName().equals("stacktrace_before_interruption.log"));
		
		
		// check if the token has been returned to the pool. In this case the second call should return the same error
		outputMessage = client.call(token.getID(), o, TestTokenHandler.class.getName(), null, null, 10);
		Assert.assertEquals(AgentErrorCode.TIMEOUT_REQUEST_INTERRUPTED,outputMessage.getAgentError().getErrorCode());
	}
	
	/**
	 * Test the token execution interruption in {@link AgentServices}
	 * @throws Exception
	 */
	@Test
	public void testTimeoutNoTokenReturn() throws Exception {
		// the argument list: set the thread as uninterruptible the sleep delay (in ms) and set   
		JsonNode o = new ObjectMapper().createObjectNode().put("delay", 5000).put("notInterruptable", true);
		
		TokenWrapper token = client.getTokenHandle(null, null, true);
		OutputMessage outputMessage = client.call(token.getID(), o, TestTokenHandler.class.getName(), null, null, 100);
		
		// assert that the call was not interrupted:
		Assert.assertEquals(AgentErrorCode.TIMEOUT_REQUEST_NOT_INTERRUPTED,outputMessage.getAgentError().getErrorCode());
		Assert.assertTrue(outputMessage.getAttachments().get(0).getName().equals("stacktrace_before_interruption.log"));
		
		// check if the token has been returned to the pool. In this case the second call should return the same error
		outputMessage = client.call(token.getID(), o, TestTokenHandler.class.getName(), null, null, 10);
		// we are now allowing for "stuck" tokens to be reused, which means we're potentially letting threads leak on the agent.
		//Assert.assertTrue(outputMessage.getError().contains("already in use"));
		
		o = newDummyJson();
		
		// the token should have been returned to the pool after execution 
		outputMessage = client.call(token.getID(), o, TestTokenHandler.class.getName(), null, null, 100);
		Assert.assertEquals(null, outputMessage.getAgentError());
		Assert.assertEquals(o, outputMessage.getPayload());
	}

	@Test
	public void testLocalToken() throws Exception {
		JsonNode o = newDummyJson();
		
		TokenWrapper token = client.getLocalTokenHandle();
		OutputMessage outputMessage = client.call(token.getID(), o, TestTokenHandler.class.getName(), null, null, 1000);
		
		Assert.assertEquals(outputMessage.getPayload(), o);;
	}
}
