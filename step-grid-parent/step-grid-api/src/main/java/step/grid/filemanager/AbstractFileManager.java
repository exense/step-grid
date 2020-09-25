/*******************************************************************************
 * Copyright (C) 2020, exense GmbH
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
 ******************************************************************************/
package step.grid.filemanager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbstractFileManager {

	private static final Logger logger = LoggerFactory.getLogger(AbstractFileManager.class);
	
	protected static final String DIRECTORY_PROPERTY = "directory";
	protected static final String ORIGINAL_FILE_PATH_PROPERTY = "originalfile";
	protected static final String META_FILENAME = "filemanager.meta";

	protected final File cacheFolder;
	
	protected ConcurrentHashMap<String, Map<FileVersionId, FileVersion>> fileHandleCache = new ConcurrentHashMap<>();
	
	public AbstractFileManager(File cacheFolder) {
		super();
		this.cacheFolder = cacheFolder;
	}

	protected void loadCache() {
		logger.info("Loading file manager client cache from data folder: "+cacheFolder.getAbsolutePath());
		if(cacheFolder.exists() && cacheFolder.isDirectory()) {
			for(File file:cacheFolder.listFiles()) {
				try {
					if(file.isDirectory()) {
						for(File container:file.listFiles()) {
							String fileId = file.getName();
							String version = container.getName();
							if(container.isDirectory()) {
								FileVersionId fileVersionId = new FileVersionId(fileId, version);
								
								Properties metaProperties = getMetaProperties(fileVersionId);
								boolean isDirectory = Boolean.parseBoolean(metaProperties.getProperty(DIRECTORY_PROPERTY));
								String originalFilePath = metaProperties.getProperty(ORIGINAL_FILE_PATH_PROPERTY);
								
								if(originalFilePath != null) {
									onFileLoad(originalFilePath, fileId);
								}
								
								File dataFile = getDataFile(fileVersionId);
								FileVersion fileVersion = new FileVersion(dataFile, fileVersionId, isDirectory);
								logger.debug("Adding file to cache. file id: "+fileId+" and version "+version);
								
								Map<FileVersionId, FileVersion> fileVersions = fileHandleCache.computeIfAbsent(fileId, f->new HashMap<FileVersionId, FileVersion>());
								fileVersions.put(fileVersionId, fileVersion);
							} else {
								logger.error("The file "+file.getAbsolutePath()+" is not a directory!");
							}
						}
					} else {
						logger.error("The file "+file.getAbsolutePath()+" is not a directory!");
					}
				} catch(Exception e) {
					logger.error("Error while loading file manager client cache for file "+file.getAbsolutePath(), e);
				}
			}
		} else if(!cacheFolder.exists()) {
			cacheFolder.mkdirs();
		}
	}
	
	protected void onFileLoad(String registryIndex, String fileId) {
		
	}
	
	protected Map<FileVersionId, FileVersion> getVersionMap(String fileId) {
		return fileHandleCache.computeIfAbsent(fileId, h->new HashMap<>());
	}
	
	protected File getFileCacheFolder(String fileId) {
		return new File(cacheFolder+"/"+fileId);
	}
	
	protected File getFileVersionCacheFolder(FileVersionId fileVersionId) {
		return new File(getFileCacheFolder(fileVersionId.fileId)+"/"+fileVersionId.version+"/");
	}

	protected File getContainerFolder(FileVersionId fileVersionId) {
		File container = new File(cacheFolder + "/" + fileVersionId.getFileId() + "/" + fileVersionId.getVersion());
		if(!container.exists()) {
			container.mkdirs();
		}
		return container;
	}
	
	protected void createMetaFile(String registryIndex, FileVersion fileVersion) throws FileManagerException {
		File metaFile = getMetaFile(fileVersion.getVersionId());
		Properties metaProperties = new Properties();
		metaProperties.setProperty(DIRECTORY_PROPERTY, Boolean.toString(fileVersion.isDirectory()));
		if(registryIndex!=null) {
			metaProperties.setProperty(ORIGINAL_FILE_PATH_PROPERTY, registryIndex);
		}
		try (FileWriter writer = new FileWriter(metaFile)) {
			metaProperties.store(writer, "");
		} catch (IOException e) {
			throw new FileManagerException(fileVersion.getVersionId(), "Error while writing meta file '"+metaFile+"'",e);
		}
	}

	private Properties getMetaProperties(FileVersionId fileVersionId) throws IOException, FileNotFoundException {
		Properties metaProperties = new Properties();
		File metaFile = getMetaFile(fileVersionId);
		try (FileInputStream stream = new FileInputStream(metaFile)) {
			metaProperties.load(stream);
		}
		return metaProperties;
	}

	private File getMetaFile(FileVersionId fileVersionId) {
		return new File(getContainerFolder(fileVersionId) + "/" + META_FILENAME);
	}
	
	private File getDataFile(FileVersionId fileVersionId) throws FileManagerException {
		File container = getContainerFolder(fileVersionId);
		File[] files = container.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return !name.equals(META_FILENAME);
			}
		});
		
		if(files.length==1) {
			return files[0];
		} else if(files.length==0) {
			throw new FileManagerException(fileVersionId, "No data file found in " + container, null);
		} else {
			throw new FileManagerException(fileVersionId, "More than one data file found in " + container, null);
		}
	}
	
	protected String getRegistryIndex(File file) {
		return file.getAbsolutePath();
	}

}
