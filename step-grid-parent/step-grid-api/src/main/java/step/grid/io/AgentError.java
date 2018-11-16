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

import java.util.Map;

public class AgentError {

	private AgentErrorCode errorCode;
	
	private Map<AgentErrorCode.Details, String> errorDetails;
	
	public AgentError() {
		super();
	}

	public AgentError(AgentErrorCode errorCode) {
		super();
		this.errorCode = errorCode;
	}

	public AgentError(AgentErrorCode errorCode, Map<AgentErrorCode.Details, String> errorDetails) {
		super();
		this.errorCode = errorCode;
		this.errorDetails = errorDetails;
	}

	public AgentErrorCode getErrorCode() {
		return errorCode;
	}

	public Map<AgentErrorCode.Details, String> getErrorDetails() {
		return errorDetails;
	}

	public void setErrorCode(AgentErrorCode errorCode) {
		this.errorCode = errorCode;
	}

	public void setErrorDetails(Map<AgentErrorCode.Details, String> errorDetails) {
		this.errorDetails = errorDetails;
	}
}
