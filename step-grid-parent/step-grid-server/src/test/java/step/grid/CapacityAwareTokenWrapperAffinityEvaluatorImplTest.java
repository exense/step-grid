package step.grid;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.junit.Test;

import junit.framework.Assert;
import step.grid.tokenpool.Identity;
import step.grid.tokenpool.IdentityImpl;
import step.grid.tokenpool.Interest;
import step.grid.tokenpool.TokenPool;
import step.grid.tokenpool.affinityevaluator.capacityaware.CapacityAwareTokenWrapperAffinityEvaluatorImpl;

public class CapacityAwareTokenWrapperAffinityEvaluatorImplTest {

	@Test
	public void test() throws TimeoutException, InterruptedException {
		String conf = this.getClass().getResource("TokenAffinityEvaluatorTest.json").getFile();
		
		HashMap<String, String> properties = new HashMap<String, String>();
		properties.put("configuration", conf);
		CapacityAwareTokenWrapperAffinityEvaluatorImpl a = new CapacityAwareTokenWrapperAffinityEvaluatorImpl();
		TokenPool<Identity, TokenWrapper> tokenPool = new TokenPool<>(a);
		a.setTokenPool(tokenPool);
		a.setProperties(properties);

		TokenWrapper token1FromAgent1 = tokenWrapper("agent1",new String[] {"color","red"}, new String[] {"shape","circle"});
		tokenPool.offerToken(token1FromAgent1);
		
		TokenWrapper token2FromAgent1 = tokenWrapper("agent1",new String[] {"color","red"}, new String[] {"shape","circle"});
		tokenPool.offerToken(token2FromAgent1);
		
		TokenWrapper token1FromAgent2 = tokenWrapper("agent2",new String[] {"color","red"});
		tokenPool.offerToken(token1FromAgent2);
		
		TokenWrapper token1FromAgent3 = tokenWrapper("agent3",new String[] {"color","blue"});
		tokenPool.offerToken(token1FromAgent3);

		TokenWrapper token2FromAgent3 = tokenWrapper("agent3",new String[] {"color","blue"});
		tokenPool.offerToken(token2FromAgent3);
		
		TokenWrapper token3FromAgent3 = tokenWrapper("agent3",new String[] {"color","blue"});
		tokenPool.offerToken(token3FromAgent3);
		
		int score;
		TokenWrapper token;
		
		score = a.getAffinityScore(pretender(new String[] {"color","red"}), token1FromAgent1);
		// token matching pretender criteria => max score (1000)
		Assert.assertEquals(1000, score);
		
		score = a.getAffinityScore(pretender(new String[] {"color","red"}, new String[] {"shape","circle"}), token1FromAgent1);
		// token matching pretender criteria => max score (1000)
		Assert.assertEquals(1000, score);
		
		score = a.getAffinityScore(pretender(new String[] {"color","red"}), token2FromAgent1);
		// token matching pretender criteria => max score (1000)
		Assert.assertEquals(1000, score);
		
		score = a.getAffinityScore(pretender(new String[] {"color","red"}), token1FromAgent2);
		// token matching pretender criteria => max score (1000)
		Assert.assertEquals(1000, score);		
		
		score = a.getAffinityScore(pretender(new String[] {"color","red"}, new String[] {"shape","circle"}), token1FromAgent2);
		// token not matching pretender criteria => -1
		Assert.assertEquals(-1, score);
		
		score = a.getAffinityScore(pretender(new String[] {"color","red"}), token1FromAgent3);
		// token not matching pretender criteria => -1
		Assert.assertEquals(-1, score);
		
		token = tokenPool.selectToken(pretender(new String[] {"color","red"}, new String[] {"shape","circle"}), 1);
		token.setState(TokenWrapperState.IN_USE);
		
		score = a.getAffinityScore(pretender(new String[] {"color","red"}), token1FromAgent1);
		// token matching pretender criteria but token usage of agent 1 reached agent capacity defined in TokenAffinityEvaluatorTest.json => -1
		Assert.assertEquals(-1, score);
		
		score = a.getAffinityScore(pretender(new String[] {"color","red"}), token2FromAgent1);
		// token matching pretender criteria but token usage of agent 1 reached agent capacity defined in TokenAffinityEvaluatorTest.json => -1
		Assert.assertEquals(-1, score);
		
		score = a.getAffinityScore(pretender(new String[] {"color","red"}), token1FromAgent2);
		// token matching pretender criteria and token usage of agent 1 = 0 => max score (1000)
		Assert.assertEquals(1000, score);
		
		// selecting token 1 from agent 2
		token = tokenPool.selectToken(pretender(new String[] {"color","red"}), 1);
		token.setState(TokenWrapperState.IN_USE);
		
		score = a.getAffinityScore(pretender(new String[] {"color","red"}), token1FromAgent2);
		Assert.assertEquals(999, score);
		
		
		// select one token from agent 3
		token = tokenPool.selectToken(pretender(new String[] {"color","blue"}), 1);
		token.setState(TokenWrapperState.IN_USE);
				
		score = a.getAffinityScore(pretender(new String[] {"color","blue"}), token1FromAgent3);
		// token matching pretender criteria but token usage of agent 3 equal to 1 => max score - 1 = 999 
		Assert.assertEquals(999, score);
		
		// select a second token from agent 3
		token = tokenPool.selectToken(pretender(new String[] {"color","blue"}), 1);
		token.setState(TokenWrapperState.IN_USE);
		
		score = a.getAffinityScore(pretender(new String[] {"color","blue"}), token1FromAgent3);
		// token matching pretender criteria and token usage of agent 3 equal to 1 => max score - 1 = 999
		Assert.assertEquals(998, score);
		
		score = a.getAffinityScore(pretender(new String[] {"color","blue"}), token2FromAgent3);
		// token matching pretender criteria and token usage of agent 3 equal to 1 => max score - 1 = 999
		Assert.assertEquals(998, score);
		
		score = a.getAffinityScore(pretender(new String[] {"color","blue"}), token3FromAgent3);
		// token matching pretender criteria and token usage of agent 3 equal to 1 => max score - 1 = 999
		Assert.assertEquals(998, score);
		
		// select the last token of agent 3
		token = tokenPool.selectToken(pretender(new String[] {"color","blue"}), 1);
		token.setState(TokenWrapperState.IN_USE);
		
		score = a.getAffinityScore(pretender(new String[] {"color","blue"}), token1FromAgent3);
		// token matching pretender criteria and token usage of agent 3 equal to 1 => max score - 1 = 997
		Assert.assertEquals(997, score);
		
		score = a.getAffinityScore(pretender(new String[] {"color","blue"}), token2FromAgent3);
		// token matching pretender criteria and token usage of agent 3 equal to 1 => max score - 1 = 997
		Assert.assertEquals(997, score);
		
		score = a.getAffinityScore(pretender(new String[] {"color","blue"}), token3FromAgent3);
		// token matching pretender criteria and token usage of agent 3 equal to 1 => max score - 1 = 997
		Assert.assertEquals(997, score);
		
	}

	protected IdentityImpl pretender(String[]... interests) {
		IdentityImpl identityImpl = new IdentityImpl();
		for (String[] strings : interests) {
			identityImpl.addInterest(strings[0], new Interest(Pattern.compile(strings[1]), true));
			
		}
		return identityImpl;
	}

	protected TokenWrapper tokenWrapper(String agentName, String[]... interests) {
		AgentRef agent = agent(agentName);
		Token token = token(agent.getAgentId());
		HashMap<String, String> attributes = new HashMap<>();
		for (String[] interest : interests) {
			attributes.put(interest[0], interest[1]);
		}
		token.setAttributes(attributes);
		return new TokenWrapper(token, agent);
	}

	protected Token token(String agentId) {
		Token token = new Token();
		token.setId(UUID.randomUUID().toString());
		token.setAgentid(agentId);
		return token;
	}

	protected AgentRef agent(String name) {
		AgentRef agent = new AgentRef();
		agent.setAgentUrl("http://"+name+":8080");
		agent.setAgentId(name);
		return agent;
	}

}
