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
		
		TIMEOUT;
	}
}
