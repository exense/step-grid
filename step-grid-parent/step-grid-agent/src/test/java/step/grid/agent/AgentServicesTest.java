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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import step.grid.agent.conf.AgentConf;
import step.grid.agent.conf.TokenConf;
import step.grid.agent.conf.TokenGroupConf;
import step.grid.io.AgentError;
import step.grid.io.AgentErrorCode;
import step.grid.io.InputMessage;
import step.grid.io.OutputMessage;

public class AgentServicesTest {

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

}
