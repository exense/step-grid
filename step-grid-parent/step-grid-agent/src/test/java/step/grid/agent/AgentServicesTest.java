package step.grid.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import junit.framework.Assert;
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
		
		List<TokenGroupConf> tokenGroups = new ArrayList<>();
		
		TokenConf tokenConf = new TokenConf();
		tokenConf.setAttributes(new HashMap<>());
		
		TokenGroupConf tokenGroup = new TokenGroupConf();
		tokenGroup.setCapacity(1);
		tokenGroup.setTokenConf(tokenConf);
		tokenGroups.add(tokenGroup);
		
		conf.setTokenGroups(tokenGroups);
		
		agent = new Agent(conf);
		agent.start();
	}
	
	@After
	public void after() throws Exception {
		agent.stop();
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
		Assert.assertEquals(AgentErrorCode.CONTEXT_BUILDER_FILE_PROVIDER_CALL_ERROR, agentError.getErrorCode());
		Assert.assertEquals("fileId",agentError.getErrorDetails().get(AgentErrorCode.Details.FILE_HANDLE));
		Assert.assertEquals("1",agentError.getErrorDetails().get(AgentErrorCode.Details.FILE_VERSION));
	}

}
