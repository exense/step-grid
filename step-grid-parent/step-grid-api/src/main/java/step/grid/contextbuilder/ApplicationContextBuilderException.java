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
package step.grid.contextbuilder;

@SuppressWarnings("serial")
public class ApplicationContextBuilderException extends Exception {

	public ApplicationContextBuilderException(String message, Throwable cause) {
		super(message, cause);
	}

	public ApplicationContextBuilderException(Throwable cause) {
		super(cause);
	}

}
