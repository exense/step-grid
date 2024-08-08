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

import static org.junit.Assert.*;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import jakarta.ws.rs.core.Response;
import org.eclipse.jetty.server.Request;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import step.grid.agent.conf.AgentConf;
import step.grid.agent.conf.TokenConf;
import step.grid.agent.conf.TokenGroupConf;
import step.grid.filemanager.FileManagerConfiguration;
import step.grid.io.AgentError;
import step.grid.io.AgentErrorCode;
import step.grid.io.InputMessage;
import step.grid.io.OutputMessage;

public class AgentServicesTest {

	private static final long GRACEFUL_SHUTDOWN_TIMEOUT = 2000l;
	Agent agent;
	
	@Before
	public void before() throws Exception {
		AgentConf conf = new AgentConf();
		conf.setAgentPort(0);
		conf.setGridHost("dummy");
		
		List<TokenGroupConf> tokenGroups = new ArrayList<>();
		
		TokenConf tokenConf = new TokenConf();
		tokenConf.setAttributes(new HashMap<>());
		
		TokenGroupConf tokenGroup = new TokenGroupConf();
		tokenGroup.setCapacity(1);
		tokenGroup.setTokenConf(tokenConf);
		tokenGroups.add(tokenGroup);
		
		conf.setTokenGroups(tokenGroups);
		conf.setGracefulShutdownTimeout(GRACEFUL_SHUTDOWN_TIMEOUT);
		conf.setFileManagerConfiguration(new FileManagerConfiguration());
		
		agent = new Agent(conf);
	}
	
	@After
	public void after() throws Exception {
		agent.close();
	}
	
	@Test
	public void test() throws Exception {
		AgentServices a = new AgentServices();
		a.agent = agent;
		a.init();
		
		String tokenId = a.tokenPool.getTokens().get(0).getUid();
		
		InputMessage message = new InputMessage();
		message.setCallTimeout(1000);
		message.setHandler(TestMessageHandler.class.getName());
		OutputMessage outputMessage = a.process(tokenId, message);
		
		AgentError agentError = outputMessage.getAgentError();
		assertEquals(AgentErrorCode.CONTEXT_BUILDER_FILE_PROVIDER_CALL_ERROR, agentError.getErrorCode());
		assertEquals("fileId",agentError.getErrorDetails().get(AgentErrorCode.Details.FILE_HANDLE));
		assertEquals("1",agentError.getErrorDetails().get(AgentErrorCode.Details.FILE_VERSION));
	}
	
	@Test
	public void testForcedShutdown() throws Exception {
		AgentServices a = new AgentServices();
		a.agent = agent;
		a.init();
		
		String tokenId = a.tokenPool.getTokens().get(0).getUid();
		a.reserveToken(tokenId);
		
		long t1 = System.currentTimeMillis();
		agent.close();
		long t2 = System.currentTimeMillis();
		// try to close a second time. This should have no effect
		agent.close();
		long t3 = System.currentTimeMillis();
		
		assertTrue(t2 - t1 >= GRACEFUL_SHUTDOWN_TIMEOUT);
		// Calling the method close a second time should have no effect.
		// Thus the duration of the call should be lower than the shutdown timeout and
		// no error should be thrown
		assertTrue(t3 - t2 < GRACEFUL_SHUTDOWN_TIMEOUT);
	}
	
	@Test
	public void testGracefullShutdown() throws Exception {
		AgentServices a = new AgentServices();
		a.agent = agent;
		a.init();
		
		String tokenId = a.tokenPool.getTokens().get(0).getUid();
		a.reserveToken(tokenId);
		
		long t1 = System.currentTimeMillis();
		new Thread(() -> {
			try {
				Thread.sleep(100);
				a.releaseToken(tokenId);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}).start();
		agent.close();
		long t2 = System.currentTimeMillis();
		
		assertTrue(t2 - t1 < GRACEFUL_SHUTDOWN_TIMEOUT);
	}

	@Test
	public void testProbes() throws InterruptedException {
		AgentServices a = new AgentServices();
		a.agent = agent;
		a.init();

		Request request = new Request(null, null);
		request.setRemoteAddr(new InetSocketAddress(0));

		Response registered = a.isRegistered(request);
		Assert.assertEquals(500,registered.getStatus());
		//Would need to start a  grid to test the positive case (or a mock of POST registrationServer + "/grid/register")

		Response running = a.isRunning(request);
		Assert.assertEquals(200,running.getStatus());


	}
}
