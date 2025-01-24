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
package step.grid.bootstrap;

import java.io.IOException;
import java.util.concurrent.Callable;

import step.grid.agent.AgentTokenServices;
import step.grid.agent.handler.MessageHandler;
import step.grid.agent.handler.MessageHandlerPool;
import step.grid.agent.tokenpool.AgentTokenWrapper;
import step.grid.contextbuilder.ApplicationContextBuilder;
import step.grid.contextbuilder.RemoteApplicationContextFactory;
import step.grid.filemanager.FileManagerClient;
import step.grid.filemanager.FileVersionId;
import step.grid.io.InputMessage;
import step.grid.io.OutputMessage;

public class BootstrapManager {
	
	ApplicationContextBuilder contextBuilder;
	
	FileManagerClient fileManager;
	
	AgentTokenServices agentTokenServices;

	public BootstrapManager(AgentTokenServices agentTokenServices, boolean isTechnicalBootstrap) {
		super();
		this.agentTokenServices = agentTokenServices;
		this.fileManager = agentTokenServices.getFileManagerClient();
		this.contextBuilder = agentTokenServices.getApplicationContextBuilder();
	}

	public OutputMessage runBootstraped(AgentTokenWrapper token, InputMessage message, String handlerClass, FileVersionId handlerPackage) throws IOException, Exception {
		contextBuilder.resetContext();

		if (message.getHandlerPackage() != null) {
			token.getTokenReservationSession().registerObjectToBeClosedWithSession(contextBuilder.pushContext(new RemoteApplicationContextFactory(fileManager, message.getHandlerPackage(), true)));
		}
		return contextBuilder.runInContext(new Callable<OutputMessage>() {
			@Override
			public OutputMessage call() throws Exception {
				ApplicationContextBuilder.ApplicationContext currentContext = contextBuilder.getCurrentContext();
				MessageHandlerPool handlerPool = (MessageHandlerPool) currentContext.computeIfAbsent("handlerPool",
						k -> new MessageHandlerPool(agentTokenServices));
				MessageHandler handler = handlerPool.get(handlerClass);
				return handler.handle(token, message);
			}
		});
	}
	
	public OutputMessage runBootstraped(AgentTokenWrapper token, InputMessage message) throws IOException, Exception {
		return runBootstraped(token, message, message.getHandler(), message.getHandlerPackage());

	}

}
