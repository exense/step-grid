package step.grid.filemanager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import step.commons.helpers.FileHelper;

/**
 * Default implementation of {@link FileManager} which stores registered {@link FileVersion} objects
 * on the file system
 *
 */
public class FileManagerImpl extends AbstractFileManager implements FileManager {
	
	private static final Logger logger = LoggerFactory.getLogger(FileManagerImpl.class);

	protected ConcurrentHashMap<File, String> fileIdRegistry = new ConcurrentHashMap<>();
	
	public FileManagerImpl(File cacheFolder) {
		super(cacheFolder);
		loadCache();
	}

	@Override
	protected void onFileLoad(File file, String fileId) {
		fileIdRegistry.put(file, fileId);
		super.onFileLoad(file, fileId);
	}
	
	@Override
	public FileVersion registerFileVersion(File file, boolean deletePreviousVersions) throws FileManagerException {
		String fileId = fileIdRegistry.computeIfAbsent(file, f->UUID.randomUUID().toString());
		long version = computeFileVersion(file);
		FileVersionId fileVersionId = new FileVersionId(fileId, version);

		if(logger.isDebugEnabled()) {
			logger.debug("Registering file '" + file + "' with version "+fileVersionId);
		}
		
		Map<FileVersionId, FileVersion> versionCache = getVersionMap(fileId);

		synchronized (versionCache) {
			if(deletePreviousVersions) {
				if(logger.isDebugEnabled()) {
					logger.debug("Removing previous versions for file '" + file + "'");
				}
				versionCache.clear();
				FileHelper.deleteFolder(getFileCacheFolder(fileId));
			}
			
			FileVersion fileVersion = versionCache.get(fileVersionId);
			
			if(fileVersion == null) {
				try {
					fileVersion = storeFile(file, fileVersionId);
				} catch (IOException e) {
					throw new FileManagerException(fileVersionId, "Error while registering file " + file.getPath(), e);
				}
				versionCache.put(fileVersionId, fileVersion);
				if(logger.isDebugEnabled()) {
					logger.debug("Registered file version '" + fileVersion + "'");
				}
			} else {
				if(logger.isDebugEnabled()) {
					logger.debug("File '" + file + "' with version "+fileVersionId + " already registered.");
				}
			}
			
			return fileVersion;
		}
	}

	private FileVersion storeFile(File source, FileVersionId fileVersionId) throws FileManagerException, IOException {
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
			FileHelper.zipDirectory(source, target);
			isDirectory = true;
		}
		
		FileVersion fileVersion = new FileVersion(target, fileVersionId, isDirectory);
		createMetaFile(source, fileVersion);
		return fileVersion;
	}

	private long computeFileVersion(File file) {
		return FileHelper.getLastModificationDateRecursive(file);
	}
	
	@Override
	public FileVersion getFileVersion(FileVersionId fileVersionId) throws FileManagerException {
		Map<FileVersionId, FileVersion> versionCache = getVersionMap(fileVersionId.getFileId());
		synchronized(versionCache) {
			FileVersion fileVersion = versionCache.get(fileVersionId);
			if(fileVersion == null) {
				return null;
			} else {
				return fileVersion;
			}
		}
	}
	
	@Override
	public void unregisterFileVersion(FileVersionId fileVersionId) {
		Map<FileVersionId, FileVersion> versionCache = getVersionMap(fileVersionId.getFileId());
		synchronized(versionCache) {
			FileVersion fileVersion = versionCache.get(fileVersionId);
			if(fileVersion != null) {
				deleteFileVersionContainer(fileVersionId);
				versionCache.remove(fileVersionId);
			}
		}
	}

	protected void deleteFileVersionContainer(FileVersionId fileVersionId) {
		FileHelper.deleteFolder(getContainerFolder(fileVersionId));
	}

	@Override
	public void cleanupCache() {
		fileHandleCache.clear();
		fileIdRegistry.clear();
		Arrays.asList(cacheFolder.listFiles()).forEach(f->FileHelper.deleteFolder(f));
	}
}
