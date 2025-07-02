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
package step.grid.client;

import step.grid.agent.handler.AbstractMessageHandler;
import step.grid.agent.handler.context.OutputMessageBuilder;
import step.grid.agent.tokenpool.AgentTokenWrapper;
import step.grid.io.InputMessage;
import step.grid.io.OutputMessage;

public class TestSessionMessageHandler extends AbstractMessageHandler {
	
	@Override
	public OutputMessage handle(AgentTokenWrapper token, InputMessage message) throws Exception {
		String key = message.getPayload().get("key").asText();
		String value = message.getPayload().get("value").asText();
		
		OutputMessageBuilder builder = new OutputMessageBuilder();
		String actualValue = token.getTokenReservationSession().get(key)!=null?token.getTokenReservationSession().get(key).toString():"";
		builder.add(key, actualValue);

		token.getTokenReservationSession().put(key, value);
		
		return builder.build();
	}

	@Override
	public void close() throws Exception {

	}
}
