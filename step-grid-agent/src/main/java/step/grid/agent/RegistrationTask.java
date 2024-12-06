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

import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import step.grid.AgentRef;

public class RegistrationTask extends TimerTask {
	
	private static final Logger logger = LoggerFactory.getLogger(RegistrationTask.class);
	
	private final Agent agent;
	
	private final RegistrationClient client;
	
	public RegistrationTask(Agent agent, RegistrationClient client) {
		super();
		this.agent = agent;
		this.client = client;
	}

	@Override
	public void run() {
		boolean agentRegistered;
		try {		
			RegistrationMessage message = new RegistrationMessage(new AgentRef(agent.getId(), agent.getAgentUrl(), AgentTypes.AGENT_TYPE), agent.getTokens());
			logger.debug("Sending registration message "+message.toString());
			agentRegistered = client.sendRegistrationMessage(message);
			agent.setRegistered(agentRegistered);
		} catch (Exception e) {
			logger.error("An unexpected error occurred while registering the adapter.",e);
		}
	}
	
	protected void unregister() {
		logger.info("Switching agent to maintenance mode to prevent new incoming sessions....");
		client.switchTokensToMaintenanceMode(agent.getTokens());
	}
	
	protected void destroy() {
		// client created and closed by caller
	}

}
