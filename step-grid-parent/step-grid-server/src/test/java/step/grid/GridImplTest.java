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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.Test;

import org.junit.Assert;
import step.grid.agent.RegistrationMessage;
import step.grid.filemanager.FileVersion;
import step.grid.tokenpool.Interest;
import step.grid.tokenpool.RegistrationCallback;

public class GridImplTest {

	@Test
	public void testCleanup() throws Exception {
		GridImpl grid = new GridImpl(0);
		grid.start();

		File testFile = File.createTempFile("GridImplTest",".txt");

		FileVersion version = grid.registerFile(testFile);

		grid.cleanupFileManagerCache();

		Assert.assertNull(grid.getRegisteredFile(version.getVersionId()));

		Assert.assertTrue(testFile.delete());
	}

	@Test
	public void test() throws Exception {
		GridImpl grid = new GridImpl(0);
		grid.start();
		
		AgentRef a = new AgentRef("dummyId", "dummyUrl", "dummyType");
		Token t1 = new Token();
		t1.setAgentid("dummyId");
		t1.setId("TokenId1");
		HashMap<String, String> attributes = new HashMap<>();
		attributes.put("att1", "val1");
		t1.setAttributes(attributes);
		ArrayList<Token> tokenList = new ArrayList<>();
		tokenList.add(t1);
		grid.handleRegistrationMessage(new RegistrationMessage(a, tokenList));
		
		List<TokenWrapper> tokens = grid.getTokens();
		Assert.assertEquals(1, tokens.size());
		
		Assert.assertTrue(grid.getAgents().contains(a));
		
		HashMap<String, Interest> interests = new HashMap<>();
		interests.put("att1", new Interest(Pattern.compile("val.*"), true));
		TokenWrapper token = grid.selectToken(attributes, interests, 10, 10, null);
		
		Assert.assertEquals(t1, token.getToken());
		
		grid.returnToken(token.getID());
	}

	@Test
	public void testCallbacks() throws Exception {
		GridImpl grid = new GridImpl(0);
		grid.start();

		AgentRef a1 = new AgentRef("a1", "dummyUrl", "dummyType");
		Token t1 = new Token();
		t1.setAgentid(a1.getAgentId());
		t1.setId("t1");
		Token t2 = new Token();
		t2.setAgentid(a1.getAgentId());
		t2.setId("t2");

		List<Token> tokens = List.of(t1,t2);
		List<Object> NONE = List.of();

		grid.handleRegistrationMessage(new RegistrationMessage(a1, tokens));

		Assert.assertEquals(List.of(a1), grid.getAgents());
		Assert.assertEquals(tokens, grid.getTokens().stream().map(TokenWrapper::getToken).collect(Collectors.toList()));

		AtomicBoolean allowAgentRegistration = new AtomicBoolean(true);
		AtomicBoolean allowTokenRegistration = new AtomicBoolean(true);

		List<Token> regTokens = new ArrayList<>();
		List<Token> unregTokens = new ArrayList<>();
		List<AgentRef> regAgents = new ArrayList<>();
		List<AgentRef> unregAgents = new ArrayList<>();
		RegistrationCallback<TokenWrapper> tokenCallback = new RegistrationCallback<>() {
			@Override
			public boolean beforeRegistering(TokenWrapper subject) {
				regTokens.add(subject.getToken());
				return allowTokenRegistration.get();
			}

			@Override
			public void afterUnregistering(List<TokenWrapper> subject) {
				subject.forEach(s -> unregTokens.add(s.getToken()));
			}
		};
		RegistrationCallback<AgentRef> agentCallback = new RegistrationCallback<>() {
			@Override
			public boolean beforeRegistering(AgentRef subject) {
				regAgents.add(subject);
				return allowAgentRegistration.get();
			}

			@Override
			public void afterUnregistering(List<AgentRef> subject) {
				unregAgents.addAll(subject);
			}
		};
		grid.addAgentRegistrationCallback(agentCallback);
		grid.addTokenRegistrationCallback(tokenCallback);

		// Everything allowed: registration should be called, grid should contain agent and tokens.
		grid.handleRegistrationMessage(new RegistrationMessage(a1, tokens));
		Assert.assertEquals(List.of(a1), grid.getAgents());
		Assert.assertEquals(List.of(a1), regAgents);
		Assert.assertEquals(NONE, unregAgents);

		Assert.assertEquals(tokens, grid.getTokens().stream().map(TokenWrapper::getToken).collect(Collectors.toList()));
		Assert.assertEquals(tokens, regTokens);
		Assert.assertEquals(NONE, unregTokens);

		regTokens.clear(); unregTokens.clear(); regAgents.clear(); unregAgents.clear();

		// Disable token registration: tokens should be *unregistered* and grid tokens empty
		allowTokenRegistration.set(false);
		grid.handleRegistrationMessage(new RegistrationMessage(a1, tokens));
		Assert.assertEquals(List.of(a1), grid.getAgents());
		Assert.assertEquals(List.of(a1), regAgents);
		Assert.assertEquals(NONE, unregAgents);

		Assert.assertEquals(NONE, grid.getTokens());
		Assert.assertEquals(tokens, regTokens);
		Assert.assertEquals(tokens, unregTokens);

		// Put everything back in place
		allowTokenRegistration.set(true);
		grid.handleRegistrationMessage(new RegistrationMessage(a1, tokens));

		regTokens.clear(); unregTokens.clear(); regAgents.clear(); unregAgents.clear();

		// Disable agent registration: both agents and tokens should be cleared. In addition, no tokens should be registered.
		allowAgentRegistration.set(false);
		grid.handleRegistrationMessage(new RegistrationMessage(a1, tokens));
		Assert.assertEquals(NONE, grid.getAgents());
		Assert.assertEquals(List.of(a1), regAgents);
		Assert.assertEquals(List.of(a1), unregAgents);

		Assert.assertEquals(NONE, grid.getTokens());
		Assert.assertEquals(NONE, regTokens);
		Assert.assertEquals(tokens, unregTokens);

		regTokens.clear(); unregTokens.clear(); regAgents.clear(); unregAgents.clear();

		// Try again. Nothing should be unregistered anymore because they have already been unregistered.
		grid.handleRegistrationMessage(new RegistrationMessage(a1, tokens));
		Assert.assertEquals(NONE, grid.getAgents());
		Assert.assertEquals(List.of(a1), regAgents);
		Assert.assertEquals(NONE, unregAgents);

		Assert.assertEquals(NONE, grid.getTokens());
		Assert.assertEquals(NONE, regTokens);
		Assert.assertEquals(NONE, unregTokens);

		regTokens.clear(); unregTokens.clear(); regAgents.clear(); unregAgents.clear();
		// Remove callbacks. Grid should return to "normal" behavior, callbacks should be unused.
		grid.removeAgentRegistrationCallback(agentCallback);
		grid.removeTokenRegistrationCallback(tokenCallback);

		grid.handleRegistrationMessage(new RegistrationMessage(a1, tokens));
		Assert.assertEquals(List.of(a1), grid.getAgents());
		Assert.assertEquals(tokens, grid.getTokens().stream().map(TokenWrapper::getToken).collect(Collectors.toList()));
		Assert.assertEquals(NONE, regAgents);
		Assert.assertEquals(NONE, unregAgents);
		Assert.assertEquals(NONE, regTokens);
		Assert.assertEquals(NONE, unregTokens);


	}


}
