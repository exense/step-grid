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
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import step.grid.TokenWrapper;
import step.grid.client.GridClientImpl.AgentCommunicationException;
import step.grid.filemanager.FileManagerException;
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;
import step.grid.io.OutputMessage;
import step.grid.tokenpool.Interest;

public interface GridClient extends Closeable {

	public TokenWrapper getLocalTokenHandle();
	
	public TokenWrapper getTokenHandle(Map<String, String> attributes, Map<String, Interest> interests, boolean createSession) throws AgentCommunicationException;
	
	public OutputMessage call(TokenWrapper tokenWrapper, JsonNode argument, String handler, FileVersionId handlerPackage, Map<String,String> properties, int callTimeout) throws Exception;
	
	public void returnTokenHandle(TokenWrapper tokenWrapper) throws AgentCommunicationException;
	
	public FileVersion registerFile(File file) throws FileManagerException;
	
	public void close();
}
