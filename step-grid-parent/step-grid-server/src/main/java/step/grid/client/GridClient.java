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
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import step.grid.GridFileService;
import step.grid.TokenWrapper;
import step.grid.client.GridClientImpl.AgentCommunicationException;
import step.grid.filemanager.FileManagerClient.FileVersionId;
import step.grid.io.OutputMessage;
import step.grid.tokenpool.Interest;

public interface GridClient extends Closeable, GridFileService {

	public TokenWrapper getLocalTokenHandle();
	
	public TokenWrapper getTokenHandle(Map<String, String> attributes, Map<String, Interest> interests, boolean createSession) throws AgentCommunicationException;
	
	public OutputMessage call(TokenWrapper tokenWrapper, JsonNode argument, String handler, FileVersionId handlerPackage, Map<String,String> properties, int callTimeout) throws Exception;
	
	public void returnTokenHandle(TokenWrapper tokenWrapper) throws AgentCommunicationException;
	
	public void close();
}
