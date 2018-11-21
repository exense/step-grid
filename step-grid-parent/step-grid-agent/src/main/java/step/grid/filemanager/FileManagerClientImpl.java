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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import step.commons.helpers.FileHelper;

public class FileManagerClientImpl implements FileManagerClient {
	
	private static final Logger logger = LoggerFactory.getLogger(FileManagerClientImpl.class);
	
	private File dataFolder;
	
	private StreamingFileProvider fileProvider;

	private Map<String, FileInfo> cache = new ConcurrentHashMap<>();
	
	public String getDataFolderPath() {
		return dataFolder.getAbsolutePath();
	}

	public FileManagerClientImpl(File dataFolder, StreamingFileProvider fileProvider) {
		super();
		logger.info("Starting file manager client using data folder: "+dataFolder.getAbsolutePath());
		this.dataFolder = dataFolder;
		this.fileProvider = fileProvider;
		
		loadCache();
	}
	
	private void loadCache() {
		logger.info("Loading file manager client cache from data folder: "+dataFolder.getAbsolutePath());
		for(File file:dataFolder.listFiles()) {
			try {
				if(file.isDirectory()) {
					for(File container:file.listFiles()) {
						String fileId = file.getName();
						String versionStr = container.getName();
						if(container.isDirectory()) {
							File[] containerFiles = container.listFiles();
							if(containerFiles.length==1) {
								FileInfo fileInfo = cache.get(fileId);
								if(fileInfo == null) {
									fileInfo = new FileInfo(fileId);
								}
								
								long version = Long.parseLong(versionStr);
								if(fileInfo.lastModified<version) {
									fileInfo.lastModified = version;
									fileInfo.file = containerFiles[0];
									cache.put(fileId, fileInfo);
									logger.debug("Adding file to cache. file id: "+fileId+" and version "+Long.toString(version));
								}
							} else {
								throw new RuntimeException("The container "+container.getAbsolutePath()+" contains more than one file!");
							}
						} else {
							throw new RuntimeException("The file "+container.getAbsolutePath()+" is not a directory!");
						}
					}
				} else {
					throw new RuntimeException("The file "+file.getAbsolutePath()+" is not a directory!");
				}
			} catch(Exception e) {
				logger.error("Error while loading file manager client cache for file "+file.getAbsolutePath(), e);
			}
		}
		
	}

	static class FileInfo {
		
		final String uid;
		
		File file;
		
		long lastModified;

		public FileInfo(String uid) {
			super();
			this.uid = uid;
		}
	}	
	
	public File requestFile(String uid, long lastModified) throws FileProviderException {
		return requestFileVersion(uid, lastModified).getFile();
	}
	
	@Override
	public FileVersion requestFileVersion(String uid, long lastModified) throws FileProviderException {
		FileVersion response = new FileVersion();
		response.setFileId(uid);
		
		if(logger.isDebugEnabled()) {
			logger.debug("Got file request for file id: "+uid+" and version "+Long.toString(lastModified));
		}
		
		FileInfo fileInfo = cache.get(uid);
		
		if(fileInfo==null) {
			fileInfo = new FileInfo(uid);
			
			FileInfo currentValue = cache.putIfAbsent(uid, fileInfo);
			if(currentValue!=null) {
				fileInfo = currentValue;
			}
		}
		
		boolean fileModication;
		
		synchronized (fileInfo) {
			if(fileInfo.file==null) {
				logger.info("Cache miss for file id: "+uid+" and version "+Long.toString(lastModified)+". Requesting file from server");
				requestFileAndUpdateCache(fileInfo, uid, lastModified);
				fileModication = true;
			} else if(lastModified>fileInfo.lastModified) {
				logger.info("File version mismatch for file id: "+uid+" and version "+Long.toString(lastModified)+". Requesting file from server");
				requestFileAndUpdateCache(fileInfo, uid, lastModified);
				fileModication = true;
			} else {
				// local file is up to date
				if(logger.isDebugEnabled()) {
					logger.debug("Served file request from cache. file id: "+uid+" and version "+Long.toString(lastModified));
				}
				fileModication = false;
			}			
		}
		
		response.setModified(fileModication);
		response.setVersion(lastModified);
		response.setFile(fileInfo.file);
		return response;
	}

	private void requestFileAndUpdateCache(FileInfo fileInfo, String uid, long lastModified) throws FileProviderException {
		File file = requestControllerFile(uid, lastModified);
		updateFileInfo(fileInfo, file, lastModified);
	}

	private void updateFileInfo(FileInfo fileInfo, File file, long lastModified) {
		fileInfo.file = file;
		fileInfo.lastModified = lastModified;
	}
	
	private File requestControllerFile(String fileId, long lastModified) throws FileProviderException {
		File container = createContainer(fileId, lastModified);		
		File file = retrieveFile(fileId, container);
		return file.getAbsoluteFile();	
	}

	private File retrieveFile(String fileId, File container) throws FileProviderException {
		long t1 = System.currentTimeMillis();
		File file = fileProvider.saveFileTo(fileId, container);
		if(logger.isDebugEnabled()) {
			logger.debug("Got file "+ fileId +" from server in "+(System.currentTimeMillis()-t1)+"ms.");
		}
		return file;
	}

	private File createContainer(String fileId, long lastModified) {
		File container = new File(dataFolder+"/"+fileId	+ "/" + lastModified);
		if(container.exists()) {
			FileHelper.deleteFolder(container);
		}
		container.mkdirs();
		FileHelper.deleteFolderOnExit(container);
		return container;
	}
}
