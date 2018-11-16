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
package step.grid.filemanager;

@SuppressWarnings("serial")
public class ControllerCallTimeout extends Exception {

	private final long timeout;

	public ControllerCallTimeout(Throwable cause, long timeout) {
		super(cause);
		this.timeout = timeout;
	}

	public long getTimeout() {
		return timeout;
	}
}
