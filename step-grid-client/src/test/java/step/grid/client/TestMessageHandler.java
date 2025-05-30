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
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;
import step.grid.io.InputMessage;
import step.grid.io.OutputMessage;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TestMessageHandler extends AbstractMessageHandler {

	public static final String TEST_TOKEN_INTERRUPTION = "testTokenInterruption";
	public static final String RESULT = "Result";
	public static final String INTERRUPTED = "Interrupted";

	@Override
	public OutputMessage handle(AgentTokenWrapper token, InputMessage message) throws Exception {
		
		if(message.getPayload().has("file")) {
			try {
				FileVersionId version = new FileVersionId(message.getPayload().get("file").asText(), message.getPayload().get("fileVersion").asText());
				FileVersion fileVersion = token.getServices().getFileManagerClient().requestFileVersion(version, true);
				OutputMessageBuilder builder = new OutputMessageBuilder();
				builder.add("content", Files.readAllLines(fileVersion.getFile().getAbsoluteFile().toPath()).get(0));
				return builder.build();
			
			} catch(Exception e) {
				e.printStackTrace();
			}
		} else if(message.getPayload().has("folder")) {
			try {
				FileVersionId version = new FileVersionId(message.getPayload().get("folder").asText(), message.getPayload().get("fileVersion").asText());
				FileVersion fileVersion = token.getServices().getFileManagerClient().requestFileVersion(version, true);
				OutputMessageBuilder builder = new OutputMessageBuilder();
				
				String content = "";
				
				
				for(String file:fileVersion.getFile().list()) {
					content += file+";";
				}
				
				builder.add("content", content);
				return builder.build();
			
			} catch(Exception e) {
				e.printStackTrace();
			}
		} else if(message.getPayload().has("testAgentCallTimeoutDuringRelease")) {
			token.getTokenReservationSession().put("myObject", new Closeable() {
				
				@Override
				public void close() throws IOException {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						
					}
				}
			});
		} else if(message.getPayload().has(TEST_TOKEN_INTERRUPTION)) {
			CountDownLatch countDownLatch = new CountDownLatch(1);
			token.getTokenReservationSession().registerEventListener(() -> countDownLatch.countDown());
			// Wait for event listener to get called
			countDownLatch.await(5, TimeUnit.SECONDS);
			OutputMessageBuilder builder = new OutputMessageBuilder();
			builder.add(RESULT, INTERRUPTED);
			return builder.build();
		}
		
		try {
			Thread.sleep(10);
		} catch (InterruptedException e) {

		}
		
		OutputMessageBuilder builder = new OutputMessageBuilder();
		builder.add(RESULT, "OK");
		
		return builder.build();
	}

	@Override
	public void close() throws Exception {

	}
}
