package step.grid.filemanager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.apache.commons.codec.digest.DigestUtils;
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

	protected ConcurrentHashMap<String, String> fileIdRegistry = new ConcurrentHashMap<>();
	
	public FileManagerImpl(File cacheFolder) {
		super(cacheFolder);
		loadCache();
	}

	@Override
	protected void onFileLoad(String registryIndex, String fileId) {
		fileIdRegistry.put(registryIndex, fileId);
		super.onFileLoad(registryIndex, fileId);
	}
	
	@Override
	public FileVersion registerFileVersion(File file, boolean deletePreviousVersions) throws FileManagerException {
		String registryIndex = getRegistryIndex(file);
		
		String fileId = fileIdRegistry.computeIfAbsent(registryIndex, f->UUID.randomUUID().toString());
		String version = computeFileVersion(file);
		
		final FileVersionId fileVersionId = new FileVersionId(fileId, version);

		return registerFileVersion(deletePreviousVersions, registryIndex, fileId, fileVersionId, () -> {
			try {
				return storeFile(file, fileVersionId);
			} catch (FileManagerException | IOException e) {
				throw new RuntimeException(e);
			}
		});
	}


	@Override
	public FileVersion registerFileVersion(InputStream inputStream, String fileName, boolean isDirectory, boolean deletePreviousVersions) throws FileManagerException {
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
		return registerFileVersion(deletePreviousVersions, registryIndex, fileId, fileVersionId, () -> {
			try {
				return storeStream(tempFile.toFile(), fileName, fileVersionId, isDirectory);
			} catch (FileManagerException | IOException e) {
				throw new RuntimeException(e);
			}
		});
	}
	

	private FileVersion registerFileVersion(boolean deletePreviousVersions, String filePath, String fileId,
			FileVersionId fileVersionId, Supplier<FileVersion> storeFunction) throws FileManagerException {
		if(logger.isDebugEnabled()) {
			logger.debug("Registering file '" + filePath + "' with version "+fileVersionId);
		}
		
		Map<FileVersionId, FileVersion> versionCache = getVersionMap(fileId);

		synchronized (versionCache) {
			if(deletePreviousVersions) {
				if(logger.isDebugEnabled()) {
					logger.debug("Removing previous versions for file '" + filePath + "'");
				}
				versionCache.clear();
				FileHelper.deleteFolder(getFileCacheFolder(fileId));
			}
			
			FileVersion fileVersion = versionCache.get(fileVersionId);
			
			if(fileVersion == null) {
				try {
					fileVersion = storeFunction.get();
				} catch (Exception e) {
					throw new FileManagerException(fileVersionId, "Error while registering file " + filePath, e);
				}
				versionCache.put(fileVersionId, fileVersion);
				if(logger.isDebugEnabled()) {
					logger.debug("Registered file version '" + fileVersion + "'");
				}
			} else {
				if(logger.isDebugEnabled()) {
					logger.debug("File '" + filePath + "' with version "+fileVersionId + " already registered.");
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
		createMetaFile(source.getAbsolutePath(), fileVersion);
		return fileVersion;
	}
	
	private FileVersion storeStream(File tempFileFromStream, String fileName, FileVersionId fileVersionId, boolean isDirectory) throws FileManagerException, IOException {
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
		createMetaFile(fileName, fileVersion);
		return fileVersion;
	}

	private String computeFileVersion(File file) {
		return Long.toString(FileHelper.getLastModificationDateRecursive(file));
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

	private String getMD5Checksum(InputStream is) throws IOException {
		return DigestUtils.md5Hex(is);
	}
}
