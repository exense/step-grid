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
package step.grid.agent.handler;

import java.util.HashMap;
import java.util.Map;

import step.grid.agent.AgentTokenServices;
import step.grid.agent.tokenpool.AgentTokenWrapper;
import step.grid.filemanager.FileManagerException;
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;
import step.grid.io.InputMessage;

public abstract class AbstractMessageHandler implements MessageHandler, AgentContextAware {
	
	protected AgentTokenServices agentTokenServices;
	
	@Override
	public void init(AgentTokenServices agentTokenServices) {
		this.agentTokenServices = agentTokenServices;
	}
	
	protected FileVersion retrieveFileVersion(String properyName, Map<String,String> properties) throws FileManagerException {
		FileVersionId fileVersionId = getFileVersionId(properyName, properties);
		if(fileVersionId!=null) {
			return agentTokenServices.getFileManagerClient().requestFileVersion(fileVersionId, true);
		} else {
			return null;
		}
	}
	
	protected FileVersionId getFileVersionId(String properyName, Map<String,String> properties) {
		String key = properyName+".id";
		if(properties.containsKey(key)) {
			String transferFileId = properties.get(key);
			String transferFileVersion = properties.get(properyName+".version");
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
