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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import ch.exense.commons.io.FileHelper;

/**
 * Default implementation of {@link FileManager} which stores registered {@link FileVersion} objects
 * on the file system
 *
 */
public class FileManagerImpl extends AbstractFileManager implements FileManager {

	private static final Logger logger = LoggerFactory.getLogger(FileManagerImpl.class);

	protected ConcurrentHashMap<String, String> fileIdRegistry = new ConcurrentHashMap<>();
	private LoadingCache<File, Long> fileModificationCache;
	
	public FileManagerImpl(File cacheFolder) {
		this(cacheFolder, new FileManagerImplConfig());
	}
	
	public FileManagerImpl(File cacheFolder, FileManagerImplConfig config) {
		super(cacheFolder, config);
		loadCache();
		
		fileModificationCache = CacheBuilder.newBuilder()
					.concurrencyLevel(config.getFileLastModificationCacheConcurrencyLevel())
					.maximumSize(config.getFileLastModificationCacheMaximumsize())
					.expireAfterWrite(config.getFileLastModificationCacheExpireAfter(), TimeUnit.MILLISECONDS)
					.build(new CacheLoader<File, Long>() {
						public Long load(File file) {
							return FileHelper.getLastModificationDateRecursive(file);
						}
					});
	}

	@Override
	protected void onFileLoad(String registryIndex, String fileId) {
		fileIdRegistry.put(registryIndex, fileId);
		super.onFileLoad(registryIndex, fileId);
	}
	
	@Override
	public FileVersion registerFileVersion(File file, boolean deletePreviousVersions, boolean cleanable) throws FileManagerException {
		String registryIndex = getRegistryIndex(file);
		
		String fileId = fileIdRegistry.computeIfAbsent(registryIndex, f->UUID.randomUUID().toString());
		String version = computeFileVersion(file);
		
		final FileVersionId fileVersionId = new FileVersionId(fileId, version);

		return registerFileVersion(deletePreviousVersions, registryIndex, fileId, fileVersionId, () -> {
			try {
				return storeFile(file, fileVersionId, cleanable);
			} catch (FileManagerException | IOException e) {
				throw new RuntimeException(e);
			}
		});
	}


	@Override
	public FileVersion registerFileVersion(InputStream inputStream, String fileName, boolean isDirectory, boolean deletePreviousVersions, boolean cleanable) throws FileManagerException {
		String registryIndex = fileName;
		
		String fileId = fileIdRegistry.computeIfAbsent(registryIndex, f->UUID.randomUUID().toString());
		String version;

		Path tempFile;
		try {
			tempFile = Files.createTempFile(fileName, "");
			Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
			try (FileInputStream is = new FileInputStream(tempFile.toFile())) {
				version = getMD5Checksum(is);
			}
		} catch (IOException e) {
			throw new FileManagerException(null, "Error while getting MD5 checksum for resource "+fileName, e);
		}
		
		final FileVersionId fileVersionId = new FileVersionId(fileId, version);
		FileVersion fileVersion = registerFileVersion(deletePreviousVersions, registryIndex, fileId, fileVersionId, () -> {
			try {
				return storeStream(tempFile.toFile(), fileName, fileVersionId, isDirectory, cleanable);
			} catch (FileManagerException | IOException e) {
				throw new RuntimeException(e);
			}
		});
		try {
			Files.deleteIfExists(tempFile);
		} catch (IOException e) {
			logger.error("Error while deleting temp file "+tempFile);
		}
		return fileVersion;
	}
	

	private FileVersion registerFileVersion(boolean deletePreviousVersions, String filePath, String fileId,
			FileVersionId fileVersionId, Supplier<CachedFileVersion> storeFunction) throws FileManagerException {
		if(logger.isDebugEnabled()) {
			logger.debug("Registering file '{}' with version {}", filePath, fileVersionId);
		}
		try {
			fileHandleCacheLock.readLock().lock();
			Map<FileVersionId, CachedFileVersion> versionCache = getVersionMap(fileId);
			synchronized (versionCache) {
				if (deletePreviousVersions) {
					if (logger.isDebugEnabled()) {
						logger.debug("Removing previous versions for file '{}'", filePath);
					}
					versionCache.clear();
					FileHelper.deleteFolder(getFileCacheFolder(fileId));
				}

				CachedFileVersion cachedFileVersion = versionCache.get(fileVersionId);
				if (cachedFileVersion == null) {
					try {
						cachedFileVersion = storeFunction.get();
					} catch (Exception e) {
					throw new FileManagerException(fileVersionId, "Error while registering file " + filePath, e);
					}
					versionCache.put(fileVersionId, cachedFileVersion);
					if (logger.isDebugEnabled()) {
						logger.debug("Registered file version '{}'", cachedFileVersion.getFileVersion());
					}
				} else {
					if (logger.isDebugEnabled()) {
						logger.debug("File '{}' with version {} already registered.", filePath, fileVersionId);
					}
				}
				cachedFileVersion.updateUsage();
				return cachedFileVersion.getFileVersion();
			}
		} finally {
			fileHandleCacheLock.readLock().unlock();
		}
	}

	protected CachedFileVersion storeFile(File source, FileVersionId fileVersionId, boolean cleanable) throws FileManagerException, IOException {
		File container = getFileVersionCacheFolder(fileVersionId);
		container.mkdirs();
		
		File target;
		boolean isDirectory;
		if(!source.isDirectory()) {
			target = new File(container.getPath()+"/"+source.getName());
			Files.copy(source.toPath(), target.toPath());
			isDirectory = false;
		} else {
			target = new File(container.getPath()+"/"+source.getName()+".zip");
			FileHelper.zip(source, target);
			isDirectory = true;
		}
		
		FileVersion fileVersion = new FileVersion(target, fileVersionId, isDirectory);
		CachedFileVersion cachedFileVersion = new CachedFileVersion(fileVersion, cleanable);
		createMetaFile(source.getAbsolutePath(), cachedFileVersion);
		return cachedFileVersion;
	}
	
	private CachedFileVersion storeStream(File tempFileFromStream, String fileName, FileVersionId fileVersionId, boolean isDirectory, boolean cleanable) throws FileManagerException, IOException {
		File container = getFileVersionCacheFolder(fileVersionId);
		container.mkdirs();
		
		File target;
		if(!isDirectory) {
			target = new File(container.getPath()+"/"+fileName);
		} else {
			target = new File(container.getPath()+"/"+fileName+".zip");
		}
		Files.move(tempFileFromStream.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		
		FileVersion fileVersion = new FileVersion(target, fileVersionId, isDirectory);
		CachedFileVersion cachedFileVersion = new CachedFileVersion(fileVersion, cleanable);
		createMetaFile(fileName, cachedFileVersion);
		return cachedFileVersion;
	}
	

	private String computeFileVersion(File file) {
		try {
			return Long.toString(fileModificationCache.get(file));
		} catch (ExecutionException e) {
			throw new RuntimeException("Error while getting last modification date for file '"+file.getAbsolutePath()+"' from cache",e);
		}
	}
	
	@Override
	public FileVersion getFileVersion(FileVersionId fileVersionId) throws FileManagerException {
		try {
			fileHandleCacheLock.readLock().lock();
			Map<FileVersionId, CachedFileVersion> versionCache = getVersionMap(fileVersionId.getFileId());
			synchronized (versionCache) {
				CachedFileVersion cachedFileVersion = versionCache.get(fileVersionId);
				if (cachedFileVersion == null) {
					return null;
				} else {
					cachedFileVersion.updateUsage();
					return cachedFileVersion.getFileVersion();
				}
			}
		} finally {
			fileHandleCacheLock.readLock().unlock();
		}
	}
	
	@Override
	public void unregisterFileVersion(FileVersionId fileVersionId) {
		removeFileVersion(fileVersionId);
	}

	@Override
	public void releaseFileVersion(FileVersion fileVersion) {
		releaseFileVersionFromCache(fileVersion);
	}

	private String getMD5Checksum(InputStream is) throws IOException {
		return DigestUtils.md5Hex(is);
	}
}
