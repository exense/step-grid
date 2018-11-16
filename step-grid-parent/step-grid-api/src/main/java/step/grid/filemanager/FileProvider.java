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

import java.io.IOException;

public interface FileProvider {
	
	public TransportableFile getTransportableFile(String fileHandle) throws IOException;
	
	public static class TransportableFile {
		
		protected String name;
		
		protected boolean isDirectory;
		
		protected byte[] bytes;

		public TransportableFile(String name, boolean isDirectory, byte[] bytes) {
			super();
			this.name = name;
			this.isDirectory = isDirectory;
			this.bytes = bytes;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public boolean isDirectory() {
			return isDirectory;
		}

		public byte[] getBytes() {
			return bytes;
		}
	}
}
