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
