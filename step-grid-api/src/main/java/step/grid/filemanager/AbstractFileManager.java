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
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import ch.exense.commons.io.FileHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbstractFileManager {

	private static final Logger logger = LoggerFactory.getLogger(AbstractFileManager.class);
	
	protected static final String DIRECTORY_PROPERTY = "directory";
	protected static final String CLEANABLE_PROPERTY = "cleanable";
	protected static final String ORIGINAL_FILE_PATH_PROPERTY = "originalfile";
	protected static final String META_FILENAME = "filemanager.meta";
	protected final File cacheFolder;
	protected ConcurrentHashMap<String, Map<FileVersionId, CachedFileVersion>> fileHandleCache = new ConcurrentHashMap<>();
	protected FileManagerConfiguration fileManagerConfiguration;
	private ScheduledExecutorService scheduledPool;
	private ScheduledFuture<?> future;
	/**
	 * This ReadWriteLock is used to synchronize operations on the whole fileHandleCache ({@link ConcurrentHashMap}).
	 * Only the cleanup task use the write lock as it is removing entries while iterating over the map. All
	 * other operations only need a read lock since they only work on single map entry.
	 */
	protected ReadWriteLock fileHandleCacheLock = new ReentrantReadWriteLock();

	public AbstractFileManager(File cacheFolder, FileManagerConfiguration fileManagerConfiguration) {
		super();
		this.cacheFolder = cacheFolder;
		this.fileManagerConfiguration = fileManagerConfiguration;
		scheduleCleanupJob();
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
								boolean isCleanable = Boolean.parseBoolean(metaProperties.getProperty(CLEANABLE_PROPERTY, "true"));
								String originalFilePath = metaProperties.getProperty(ORIGINAL_FILE_PATH_PROPERTY);
								
								if(originalFilePath != null) {
									onFileLoad(originalFilePath, fileId);
								}
								
								File dataFile = getDataFile(fileVersionId);
								FileVersion fileVersion = new FileVersion(dataFile, fileVersionId, isDirectory);
								CachedFileVersion cachedFileVersion = new CachedFileVersion(fileVersion, isCleanable);
								logger.debug("Adding file to cache. file id: "+fileId+" and version "+version);
								
								Map<FileVersionId, CachedFileVersion> fileVersions = fileHandleCache.computeIfAbsent(fileId, f->new ConcurrentHashMap<FileVersionId, CachedFileVersion>());
								fileVersions.put(fileVersionId, cachedFileVersion);
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
	
	protected Map<FileVersionId, CachedFileVersion> getVersionMap(String fileId) {
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
	
	protected void createMetaFile(String registryIndex, CachedFileVersion cachedFileVersion) throws FileManagerException {
		FileVersion fileVersion = cachedFileVersion.getFileVersion();
		File metaFile = getMetaFile(fileVersion.getVersionId());
		Properties metaProperties = new Properties();
		metaProperties.setProperty(DIRECTORY_PROPERTY, Boolean.toString(fileVersion.isDirectory()));
		metaProperties.setProperty(CLEANABLE_PROPERTY, Boolean.toString(cachedFileVersion.isCleanable()));
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

	protected boolean deleteFileVersionContainer(FileVersionId fileVersionId) {
		return FileHelper.safeDeleteFolder(getContainerFolder(fileVersionId));
	}

	protected void releaseFileVersionFromCache(FileVersion fileVersion){
		Map<FileVersionId, CachedFileVersion> versionCache = getVersionMap(fileVersion.getFileId());
		synchronized (versionCache) {
			CachedFileVersion cachedFileVersion = versionCache.get(fileVersion.getVersionId());
			if (cachedFileVersion != null) {
				int currentUsage = cachedFileVersion.releaseUsage();
				if (logger.isDebugEnabled()) {
					logger.debug("File version {} founds in cache, decrementing usage to {}", cachedFileVersion.getFileVersion(), currentUsage);
				}
				if (getCacheTTLms() == 0 && currentUsage == 0 && cachedFileVersion.isCleanable()) {
					if (logger.isDebugEnabled()) {
						logger.debug("Usage reached 0 after decremening and TTL is set to 0 directly removing {} from cache.", cachedFileVersion.getFileVersion());
					}
					removeFileVersion(fileVersion.getVersionId());
				}
			} else {
				logger.warn("Release file version was called for {} which was not found in cache", fileVersion);
			}
		}
	}

	protected void removeFileVersion(FileVersionId fileVersionId) {
		try {
			fileHandleCacheLock.readLock().lock();
			Map<FileVersionId, CachedFileVersion> versionCache = getVersionMap(fileVersionId.getFileId());
			synchronized (versionCache) {
				FileVersion fileVersion = versionCache.get(fileVersionId).getFileVersion();
				if (logger.isDebugEnabled()) {
					logger.debug("Removing File version {} from cache", fileVersion);
				}
				if (fileVersion != null) {
					deleteFileVersionContainer(fileVersionId);
					versionCache.remove(fileVersionId);
				}
				if (versionCache.isEmpty()) {
					File fileCacheFolder = getFileCacheFolder(fileVersionId.getFileId());
					if (fileCacheFolder.exists()) {
						FileHelper.deleteFolder(fileCacheFolder);
					}
					fileHandleCache.remove(fileVersionId.getFileId());
				}
			}
		} finally {
			fileHandleCacheLock.readLock().unlock();
		}
	}

	public long getCacheTTLms() {
		return fileManagerConfiguration.getConfigurationTimeUnit().toMillis(fileManagerConfiguration.getCleanupLastAccessTimeThresholdMinutes());
	}

	public void cleanupCache() {
		try {
			fileHandleCacheLock.writeLock().lock();
			long cacheTTLms = getCacheTTLms();
			final long fromLastAccessTime = System.currentTimeMillis() - (cacheTTLms);
			logger.info("Starting cleanup of file manager. Removing cleanable files older than " + new Date(fromLastAccessTime));
			AtomicInteger atomicInteger = new AtomicInteger();
			Iterator<Map.Entry<String, Map<FileVersionId, CachedFileVersion>>> fileHandleCacheIterator = fileHandleCache.entrySet().iterator();
			while (fileHandleCacheIterator.hasNext()) {
				Map.Entry<String, Map<FileVersionId, CachedFileVersion>> fileHandleEntry = fileHandleCacheIterator.next();
				String fileId = fileHandleEntry.getKey();
				Map<FileVersionId, CachedFileVersion> versionCache = fileHandleEntry.getValue();
				synchronized (versionCache) { //should not be required with the new ReadWriteLock but doesn't hurt
					Iterator<Map.Entry<FileVersionId, CachedFileVersion>> iterator = versionCache.entrySet().iterator();
					while (iterator.hasNext()) {
						Map.Entry<FileVersionId, CachedFileVersion> next = iterator.next();
						CachedFileVersion cachedFileVersion = next.getValue();
						if (cachedFileVersion.isCleanable() && cachedFileVersion.getLastAccessTime() < fromLastAccessTime
						 				&& cachedFileVersion.getCurrentUsageCount() == 0) {
							if (deleteFileVersionContainer(next.getKey())) {
								iterator.remove();
								atomicInteger.incrementAndGet();
							} else {
								logger.error("Cannot delete the file manager folder " + getContainerFolder(next.getKey()) +
										", skipping cleanup of this entry.");
							}
						}
					}
					File fileCacheFolder = getFileCacheFolder(fileId);
					if (versionCache.isEmpty()) {
						if (fileCacheFolder.exists()) {
							FileHelper.deleteFolder(fileCacheFolder);
						}
						fileHandleCacheIterator.remove();
					}
				}
			}
			logger.info("Cleanup of file manager completed. " + atomicInteger.get() + " files removed.");
		} finally {
			fileHandleCacheLock.writeLock().unlock();
		}
	}

	/**
	 * Schedule the cache cleanup job with the frequency defined with {@link FileManagerConfiguration#getCleanupIntervalMinutes()}.
	 * <p>It can be disabled using the {@link FileManagerConfiguration#isCleanupJobEnabled()} flag.</p>
	 * <p>The cleanup job browses all entries from the cache and remove the one marked as cleanable and not accessed for the period of time defined with {@link FileManagerConfiguration#getCleanupLastAccessTimeThresholdMinutes()}</p>
	 *
	 */
	protected void scheduleCleanupJob() {
		if (fileManagerConfiguration.isCleanupJobEnabled()) {
			long cleanupIntervalMinutes = fileManagerConfiguration.getCleanupIntervalMinutes();
			scheduledPool = Executors.newScheduledThreadPool(1);
			future = scheduledPool.scheduleAtFixedRate(() -> {
				Thread.currentThread().setName("FileManagerCleanupThread");
				try {
					this.cleanupCache();
				} catch (Throwable e) {
					logger.error("Unhandled error while running the file manager clean up task.", e);
				}
			}, cleanupIntervalMinutes, cleanupIntervalMinutes, fileManagerConfiguration.getConfigurationTimeUnit());
		}
	}

	public void close() throws Exception {
		if (future != null) {
			future.cancel(false);
		}
		if (scheduledPool != null) {
			scheduledPool.shutdown();
			try {
				scheduledPool.awaitTermination(1, fileManagerConfiguration.getConfigurationTimeUnit());
			} catch (InterruptedException e) {
				logger.error("Timeout occurred while stopping the file manager clean up task.");
			}
		}
	}

}
