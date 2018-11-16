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
package step.grid.agent;

import java.util.Map;

import step.grid.contextbuilder.ApplicationContextBuilder;
import step.grid.filemanager.FileManagerClient;

public class AgentTokenServices {
	
	FileManagerClient fileManagerClient;
		
	Map<String,String> agentProperties;
	
	ApplicationContextBuilder applicationContextBuilder;
	
	public AgentTokenServices(FileManagerClient fileManagerClient) {
		super();
		
		this.fileManagerClient = fileManagerClient;
	}

	public FileManagerClient getFileManagerClient() {
		return fileManagerClient;
	}

	public Map<String, String> getAgentProperties() {
		return agentProperties;
	}

	public void setAgentProperties(Map<String, String> agentProperties) {
		this.agentProperties = agentProperties;
	}

	public ApplicationContextBuilder getApplicationContextBuilder() {
		return applicationContextBuilder;
	}

	public void setApplicationContextBuilder(ApplicationContextBuilder applicationContextBuilder) {
		this.applicationContextBuilder = applicationContextBuilder;
	}
}
