package step.grid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Test;

import junit.framework.Assert;
import step.grid.agent.RegistrationMessage;
import step.grid.tokenpool.Interest;

public class GridImplTest {

	@Test
	public void test() throws Exception {
		GridImpl grid = new GridImpl(0);
		grid.start();
		
		AgentRef a = new AgentRef("dummyId", "dummyUrl", "dummyType");
		Token t1 = new Token();
		t1.setAgentid("dummyId");
		t1.setId("TokenId1");
		HashMap<String, String> attributes = new HashMap<String, String>();
		attributes.put("att1", "val1");
		t1.setAttributes(attributes);
		ArrayList<Token> tokenList = new ArrayList<Token>();
		tokenList.add(t1);
		grid.handleRegistrationMessage(new RegistrationMessage(a, tokenList));
		
		List<step.grid.tokenpool.Token<TokenWrapper>> tokens = grid.getTokens();
		Assert.assertEquals(1, tokens.size());
		
		Assert.assertTrue(grid.getAgents().contains(a));
		
		HashMap<String, Interest> interests = new HashMap<String, Interest>();
		interests.put("att1", new Interest(Pattern.compile("val.*"), true));
		TokenWrapper token = grid.selectToken(attributes, interests, 10, 10);
		
		Assert.assertEquals(t1, token.getToken());
		
		grid.returnToken(token);
	}

}
