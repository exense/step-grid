/*******************************************************************************
 * (C) Copyright 2018 Jerome Comte and Dorian Cransac
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
 *******************************************************************************/
package step.grid.client;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import step.grid.agent.handler.AbstractMessageHandler;
import step.grid.agent.handler.context.OutputMessageBuilder;
import step.grid.agent.tokenpool.AgentTokenWrapper;
import step.grid.io.InputMessage;
import step.grid.io.OutputMessage;

public class TestMessageHandler extends AbstractMessageHandler {
	
	@Override
	public OutputMessage handle(AgentTokenWrapper token, InputMessage message) throws Exception {
		
		if(message.getPayload().has("file")) {
			try {
				File file = token.getServices().getFileManagerClient().requestFile(message.getPayload().get("file").asText(), message.getPayload().get("fileVersion").asInt());
				OutputMessageBuilder builder = new OutputMessageBuilder();
				builder.add("content", Files.readAllLines(file.toPath()).get(0));
				return builder.build();
			
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		
		token.getTokenReservationSession().put("myObject", new Closeable() {
			
			@Override
			public void close() throws IOException {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {

				}
			}
		});
		
		try {
			Thread.sleep(10);
		} catch (InterruptedException e) {

		}
		
		return null;
	}

}
