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
public class FileProviderException extends Exception {

	private final String fileHandle;
	
	public FileProviderException(String fileHandle, Throwable cause) {
		super(cause);
		this.fileHandle = fileHandle;
	}

	public String getFileHandle() {
		return fileHandle;
	}
}
