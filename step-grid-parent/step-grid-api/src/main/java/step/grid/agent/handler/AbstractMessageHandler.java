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
package step.grid.agent.handler;

import java.util.HashMap;
import java.util.Map;

import step.grid.agent.AgentTokenServices;
import step.grid.agent.tokenpool.AgentTokenWrapper;
import step.grid.filemanager.FileManagerClient.FileVersion;
import step.grid.filemanager.FileManagerClient.FileVersionId;
import step.grid.filemanager.FileProviderException;
import step.grid.io.InputMessage;

public abstract class AbstractMessageHandler implements MessageHandler, AgentContextAware {
	
	protected AgentTokenServices agentTokenServices;
	
	@Override
	public void init(AgentTokenServices agentTokenServices) {
		this.agentTokenServices = agentTokenServices;
	}
	
	protected FileVersion retrieveFileVersion(String properyName, Map<String,String> properties) throws FileProviderException {
		FileVersionId fileVersionId = getFileVersionId(properyName, properties);
		if(fileVersionId!=null) {
			return agentTokenServices.getFileManagerClient().requestFileVersion(fileVersionId.getFileId(), fileVersionId.getVersion());			
		} else {
			return null;
		}
	}
	
	protected FileVersionId getFileVersionId(String properyName, Map<String,String> properties) {
		String key = properyName+".id";
		if(properties.containsKey(key)) {
			String transferFileId = properties.get(key);
			long transferFileVersion = Long.parseLong(properties.get(properyName+".version"));
			return new FileVersionId(transferFileId, transferFileVersion);			
		} else {
			return null;
		}
	}
	
	protected Map<String, String> buildPropertyMap(AgentTokenWrapper token, InputMessage message) {
		Map<String, String> properties = new HashMap<>();
		if(message.getProperties()!=null) {
			properties.putAll(message.getProperties());
		}
		if(token.getProperties()!=null) {
			properties.putAll(token.getProperties());			
		}
		return properties;
	}
}
