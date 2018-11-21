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
