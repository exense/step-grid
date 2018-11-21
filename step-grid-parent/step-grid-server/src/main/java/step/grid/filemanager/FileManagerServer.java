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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import step.commons.helpers.FileHelper;

public class FileManagerServer implements FileProvider {

	ConcurrentHashMap<String, File> registry = new ConcurrentHashMap<>();
	
	ConcurrentHashMap<File, String> reverseRegistry = new ConcurrentHashMap<>();
	
	public String registerFile(File file) {
		if(!file.exists()||!file.canRead()) {
			throw new RuntimeException("Unable to find or read file "+file.getAbsolutePath());
		}

		String handle = reverseRegistry.computeIfAbsent(file, new Function<File, String>() {
			@Override
			public String apply(File t) {
				String handle = UUID.randomUUID().toString();
				registry.put(handle, file);
				return handle;
			}
		});
		return handle;
	}
	
	@Override
	public TransportableFile getTransportableFile(String fileHandle) {
		File transferFile = getFile(fileHandle);
		byte[] bytes;
		boolean isDirectory;
		try {
			if(transferFile.isDirectory()) {
				bytes = FileHelper.zipDirectory(transferFile);
				isDirectory = true;
			} else {
				bytes = Files.readAllBytes(transferFile.toPath());	
				isDirectory = false;
			}			
			return new TransportableFile(transferFile.getName(), isDirectory, bytes);
		} catch (IOException e) {
			throw new RuntimeException("Error while reading file with handle "+fileHandle+" mapped to '"+transferFile.getAbsolutePath()+"'", e);
		}
	}
	
	public File getFile(String fileHandle) {
		return registry.get(fileHandle);
	}
	
}
