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
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.exense.commons.io.FileHelper;

/**
 * Default implementation of {@link FileManagerClient} which delegates the retrieval of {@link FileVersion} to
 * a {@link FileVersionProvider}
 *
 */
public class FileManagerClientImpl extends AbstractFileManager implements FileManagerClient {

	private static final Logger logger = LoggerFactory.getLogger(FileManagerClientImpl.class);
	
	protected FileVersionProvider fileProvider;
	
	/**
	 * @param cacheFolder the folder to be used to store the {@link FileVersion}s
	 * @param fileProvider the file provider responsible for the retrieval of the {@link FileVersion} if absent of the cache 
	 */
	public FileManagerClientImpl(File cacheFolder, FileVersionProvider fileProvider, FileManagerConfiguration fileManagerConfiguration) {
		super(cacheFolder, fileManagerConfiguration);
		this.fileProvider = fileProvider;
		loadCache();
	}

	@Override
	public FileVersion requestFileVersion(FileVersionId fileVersionId, boolean cleanable) throws FileManagerException {
		Map<FileVersionId, FileVersion> versionCache = getVersionMap(fileVersionId.getFileId());
		synchronized(versionCache) {
			FileVersion fileVersion = versionCache.get(fileVersionId);
			if(fileVersion == null) {
				if(fileProvider != null) {
					File container = getContainerFolder(fileVersionId);
					
					long t1 = System.currentTimeMillis();
					fileVersion = fileProvider.saveFileVersionTo(fileVersionId, container);
					fileVersion.setCleanable(cleanable);
					if(logger.isDebugEnabled()) {
						logger.debug("Retrieved file version "+fileVersion+" in "+Long.toString(System.currentTimeMillis()-t1)+"ms");
					}
					
					createMetaFile(null, fileVersion);
					versionCache.put(fileVersionId, fileVersion);
					return fileVersion;
				} else {
					return null;
				}
			} else {
				fileVersion.updateLastAccessTime();
				return fileVersion;
			}
		}
	}
	
	@Override
	public void removeFileVersionFromCache(FileVersionId fileVersionId) {
		removeFileVersion(fileVersionId);
	}
}
