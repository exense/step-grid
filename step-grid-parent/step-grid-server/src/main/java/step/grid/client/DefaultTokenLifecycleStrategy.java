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
