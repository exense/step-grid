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

import step.grid.tokenpool.TokenRegistry;

public class TokenLifecycleStrategyCallback {

	private TokenRegistry tokenRegistry;
	
	private String tokenId;
	
	public TokenLifecycleStrategyCallback(TokenRegistry tokenRegistry, String tokenId) {
		super();
		this.tokenRegistry = tokenRegistry;
		this.tokenId = tokenId;
	}

	public void addTokenError(String errorMessage, Exception exception) {
		tokenRegistry.markTokenAsFailing(tokenId, errorMessage, exception);
	}

}
