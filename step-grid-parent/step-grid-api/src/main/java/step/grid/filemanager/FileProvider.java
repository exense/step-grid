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
