/*******************************************************************************
 * (C) Copyright 2016, 2018 Jerome Comte and Dorian Cransac
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
package step.grid.io;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import step.grid.filemanager.FileManagerClient.FileVersionId;

public class InputMessage {

	private String handler;
	
	private FileVersionId handlerPackage;

	private Map<String, String> properties;

	private int callTimeout;

	private JsonNode payload;

	public InputMessage() {
		super();
	}

	/**
	 * @return the name of the handler class to be used to handle this message
	 */
	public String getHandler() {
		return handler;
	}

	public void setHandler(String handler) {
		this.handler = handler;
	}

	/**
	 * @return the payload of this message as {@link JsonNode}
	 */
	public JsonNode getPayload() {
		return payload;
	}

	public void setPayload(JsonNode argument) {
		this.payload = argument;
	}

	/**
	 * @return the properties attached to this message
	 */
	public Map<String, String> getProperties() {
		return properties;
	}

	public void setProperties(Map<String, String> properties) {
		this.properties = properties;
	}

	/**
	 * @return the call timeout for the processing of this message in ms
	 */
	public int getCallTimeout() {
		return callTimeout;
	}

	public void setCallTimeout(int callTimeout) {
		this.callTimeout = callTimeout;
	}

	/**
	 * @return the handle to the remote package (jar) containing the handler. Optional.
	 */
	public FileVersionId getHandlerPackage() {
		return handlerPackage;
	}

	public void setHandlerPackage(FileVersionId handlerPackage) {
		this.handlerPackage = handlerPackage;
	}
	
	
}
