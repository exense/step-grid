/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
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
