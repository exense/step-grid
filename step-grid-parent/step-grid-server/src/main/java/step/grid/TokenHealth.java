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
package step.grid;

public class TokenHealth {

	protected TokenWrapperOwner tokenWrapperOwner;
	
	protected String errorMessage;
	
	protected Exception exception;
	
	public TokenWrapperOwner getTokenWrapperOwner() {
		return tokenWrapperOwner;
	}

	public void setTokenWrapperOwner(TokenWrapperOwner tokenWrapperOwner) {
		this.tokenWrapperOwner = tokenWrapperOwner;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public Exception getException() {
		return exception;
	}

	public void setException(Exception exception) {
		this.exception = exception;
	}
}
