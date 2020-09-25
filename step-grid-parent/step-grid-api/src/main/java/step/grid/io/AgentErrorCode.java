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
package step.grid.io;

public enum AgentErrorCode {

	TIMEOUT_REQUEST_NOT_INTERRUPTED, // Timeout while processing request. WARNING: Request execution couldn't be interrupted 
	
	TIMEOUT_REQUEST_INTERRUPTED, //Timeout while processing request. Request execution interrupted successfully.";
	
	TOKEN_NOT_FOUND, //Token not found";
	
	CONTEXT_BUILDER, //Unexpected error while building execution context";
	
	CONTEXT_BUILDER_FILE_PROVIDER_CALL_TIMEOUT, //Error while building execution context due to a call timeout to the controller during file download";
	
	CONTEXT_BUILDER_FILE_PROVIDER_CALL_ERROR, //Error while building execution context due to a connection error during file download";
	
	UNEXPECTED; //Unexpected error";
	
	public enum Details {
		
		FILE_HANDLE,
		
		FILE_VERSION,
		
		TIMEOUT;
	}
}
