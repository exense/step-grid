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
package step.grid.tokenpool;

import java.util.List;
import java.util.concurrent.TimeoutException;

import step.grid.TokenWrapper;


public interface TokenRegistry {

	TokenWrapper selectToken(Identity pretender, long matchTimeout, long noMatchTimeout)
			throws TimeoutException, InterruptedException;

	void returnToken(TokenWrapper object);

	List<step.grid.tokenpool.Token<TokenWrapper>> getTokens();
	
	void markTokenAsFailing(String tokenId, String errorMessage, Exception e);

}