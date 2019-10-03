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
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import step.grid.GridFileService;
import step.grid.TokenWrapper;
import step.grid.TokenWrapperOwner;
import step.grid.client.AbstractGridClientImpl.AgentCommunicationException;
import step.grid.filemanager.FileVersionId;
import step.grid.io.OutputMessage;
import step.grid.tokenpool.Interest;

public interface GridClient extends GridFileService, Closeable {
	
	/**
	 * @return a local {@link TokenWrapper} that runs in the local JVM
	 */
	public TokenWrapper getLocalTokenHandle();
	
	/**
	 * Selects a remote token from the GRID based on the attributes and selection criteria
	 * 
	 * @param attributes the "pretender" attributes that are matched against the selection criteria of the token. This map can be empty in most of the cases.
	 * @param selectionCriteria the token selection criteria used to select the token 
	 * @param createSession if a Session should be created on the agent side
	 * @return a {@link TokenWrapper} from the GRID that executes calls on any available agent
	 * @throws AgentCommunicationException
	 */
	public TokenWrapper getTokenHandle(Map<String, String> attributes, Map<String, Interest> selectionCriteria, boolean createSession) throws AgentCommunicationException;
	
	/**
	 * Selects a remote token from the GRID based on the attributes and selection criteria
	 * 
	 * @param attributes the "pretender" attributes that are matched against the selection criteria of the tokenThis map can be empty in most of the cases.
	 * @param selectionCriteria the token selection criteria used to select the token 
	 * @param createSession if a Session should be created on the agent side
	 * @param tokenOwner a description of the requester of the token. After successful selection the token will be marked as owned by this requester.
	 * @return a {@link TokenWrapper} from the GRID that executes calls on any available agent
	 * @throws AgentCommunicationException
	 */
	public TokenWrapper getTokenHandle(Map<String, String> attributes, Map<String, Interest> selectionCriteria, boolean createSession, TokenWrapperOwner tokenOwner) throws AgentCommunicationException;
	
	/**
	 * Runs the specified handler class on a specific token 
	 * 
	 * @param tokenId the id of the token to run the handler on
	 * @param argument the argument to be passed to the handler
	 * @param handler the classname of the handler
	 * @param handlerPackage the description of the package containing the handler
	 * @param properties the properties to be passed to the handler in addition to the argument
	 * @param callTimeout the calltimeout in ms
	 * @return the {@link OutputMessage} returned by the handler after execution
	 * @throws GridClientException
	 * @throws AgentCommunicationException
	 * @throws Exception
	 */
	public OutputMessage call(String tokenId, JsonNode argument, String handler, FileVersionId handlerPackage, Map<String,String> properties, int callTimeout) throws GridClientException, AgentCommunicationException, Exception;
	
	/**
	 * Return the token to the pool
	 * 
	 * @param tokenId the ID of the {@link TokenWrapper} to be returned. The ID is returned by {@link TokenWrapper#getID}
	 * @throws GridClientException
	 * @throws AgentCommunicationException
	 */
	public void returnTokenHandle(String tokenId) throws GridClientException, AgentCommunicationException;
	
	public void close();
	
}
