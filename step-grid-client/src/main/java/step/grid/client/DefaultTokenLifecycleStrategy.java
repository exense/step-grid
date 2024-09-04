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
package step.grid.client;

import step.grid.TokenWrapper;
import step.grid.io.AgentErrorCode;
import step.grid.io.OutputMessage;

public class DefaultTokenLifecycleStrategy implements TokenLifecycleStrategy {

	@Override
	public void afterTokenReleaseError(TokenLifecycleStrategyCallback callback, TokenWrapper tokenWrapper,
			Exception e) {
		callback.addTokenError("Error while releasing token",e);
	}

	@Override
	public void afterTokenReservationError(TokenLifecycleStrategyCallback callback, TokenWrapper tokenWrapper,
			Exception e) {
		callback.addTokenError("Error while reserving token",e);
	}

	@Override
	public void afterTokenCallError(TokenLifecycleStrategyCallback callback, TokenWrapper tokenWrapper, Exception e) {
		callback.addTokenError("Error while calling agent", e);
	}

	@Override
	public void afterTokenCall(TokenLifecycleStrategyCallback callback, TokenWrapper tokenWrapper,
			OutputMessage outputMessage) {
		if(outputMessage!=null && outputMessage.getAgentError()!=null) {
			if(outputMessage.getAgentError().getErrorCode().equals(AgentErrorCode.TIMEOUT_REQUEST_NOT_INTERRUPTED)) {
				callback.addTokenError("Error while calling agent", null);
			}
		}
	}
}
