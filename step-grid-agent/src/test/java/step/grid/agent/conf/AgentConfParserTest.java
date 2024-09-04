package step.grid.agent.conf;

import static org.junit.Assert.*;

import org.junit.Test;

import ch.exense.commons.app.ArgumentParser;
import ch.exense.commons.io.FileHelper;
import step.grid.agent.Agent;
import step.grid.app.configuration.ConfigurationParser;

public class AgentConfParserTest {

	@Test
	public void testYaml() throws Exception {
		ConfigurationParser<AgentConf> agentConfParser = new ConfigurationParser<AgentConf>();
		AgentConf agentConf = agentConfParser.parse(new ArgumentParser(new String[] {}),
				FileHelper.getClassLoaderResourceAsFile(this.getClass().getClassLoader(), "AgentConf.yaml"),
				AgentConf.class);
		assertAgentConf(agentConf);
	}
	
	@Test
	public void testYamlWithPlaceholders() throws Exception {
		ConfigurationParser<AgentConf> agentConfParser = new ConfigurationParser<AgentConf>();
		AgentConf agentConf = agentConfParser.parse(new ArgumentParser(new String[] {"-host=localhost"}),
				FileHelper.getClassLoaderResourceAsFile(this.getClass().getClassLoader(), "AgentConfWithPlaceholders.yaml"),
				AgentConf.class);
		assertAgentConf(agentConf);
	}
	
	@Test
	public void testJson() throws Exception {
		ConfigurationParser<AgentConf> agentConfParser = new ConfigurationParser<AgentConf>();
		AgentConf agentConf = agentConfParser.parse(new ArgumentParser(new String[] {}),
				FileHelper.getClassLoaderResourceAsFile(this.getClass().getClassLoader(), "AgentConf.json"),
				AgentConf.class);
		assertAgentConf(agentConf);
	}
	
	@Test
	public void testInvalidFile() throws Exception {
		ConfigurationParser<AgentConf> agentConfParser = new ConfigurationParser<AgentConf>();
		Exception actualException = null;
		try {
			agentConfParser.parse(new ArgumentParser(new String[] {}),
					FileHelper.getClassLoaderResourceAsFile(this.getClass().getClassLoader(), "AgentConf.properties"),
					AgentConf.class);
		} catch (Exception e) {
			actualException = e;
		}
		assertNotNull(actualException);
		assertTrue(actualException.getMessage().startsWith("Unsupported file type"));
	}
	
	private void assertAgentConf(AgentConf agentConf) {
		assertEquals("http://localhost:8081", agentConf.getGridHost());
		assertEquals(1000, (int) agentConf.getRegistrationPeriod());
		assertEquals(1, (int) agentConf.getTokenGroups().size());
	}

}
