package step.grid.filemanager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import step.commons.helpers.FileHelper;

/**
 * A file-based cache for {@link FileVersion} objects. 
 * 
 * This cache enables the registration, caching and retrieval of different versions of files.
 *
 */
public class FileManagerImpl implements FileManager {

	private static final Logger logger = LoggerFactory.getLogger(FileManagerImpl.class);
	
	private static final String DIRECTORY_PROPERTY = "directory";
	private static final String ORIGINAL_FILE_PATH_PROPERTY = "originalfile";

	private static final String META_FILENAME = "filemanager.meta";
	
	protected ConcurrentHashMap<File, String> fileIdRegistry = new ConcurrentHashMap<>();

	protected ConcurrentHashMap<String, Map<FileVersionId, FileVersion>> fileHandleCache = new ConcurrentHashMap<>();
	
	protected final File cacheFolder;
	
	protected FileVersionProvider fileProvider;
	
	/**
	 * Creates a new instance of {@link FileManagerImpl} without fileProvider.
	 * This means that the method requestFile will return null if a FileVersion hasn't been 
	 * explicitly registered first 
	 * 
	 * @param cacheFolder the folder to be used to store the {@link FileVersion}s
	 */
	public FileManagerImpl(File cacheFolder) {
		this(cacheFolder, null);
	}
	
	/**
	 * @param cacheFolder the folder to be used to store the {@link FileVersion}s
	 * @param fileProvider the file provider responsible for the retrieval of the {@link FileVersion} if absent of the cache 
	 */
	public FileManagerImpl(File cacheFolder, FileVersionProvider fileProvider) {
		super();
		this.cacheFolder = cacheFolder;
		this.fileProvider = fileProvider;
		loadCache();
	}
	
	private void loadCache() {
		logger.info("Loading file manager client cache from data folder: "+cacheFolder.getAbsolutePath());
		if(cacheFolder.exists() && cacheFolder.isDirectory()) {
			for(File file:cacheFolder.listFiles()) {
				try {
					if(file.isDirectory()) {
						for(File container:file.listFiles()) {
							String fileId = file.getName();
							String versionStr = container.getName();
							if(container.isDirectory()) {
								long version = Long.parseLong(versionStr);
								FileVersionId fileVersionId = new FileVersionId(fileId, version);
								
								Properties metaProperties = getMetaProperties(fileVersionId);
								boolean isDirectory = Boolean.parseBoolean(metaProperties.getProperty(DIRECTORY_PROPERTY));
								String originalFilePath = metaProperties.getProperty(ORIGINAL_FILE_PATH_PROPERTY);
								
								if(originalFilePath != null) {
									File originalFile = new File(originalFilePath);
									fileIdRegistry.put(originalFile, fileId);
								}
								
								File dataFile = getDataFile(fileVersionId);
								FileVersion fileVersion = new FileVersion(dataFile, fileVersionId, isDirectory);
								logger.debug("Adding file to cache. file id: "+fileId+" and version "+Long.toString(version));
								
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

	private long computeFileVersion(File file) {
		return FileHelper.getLastModificationDateRecursive(file);
	}

	private Map<FileVersionId, FileVersion> getVersionMap(String fileId) {
		return fileHandleCache.computeIfAbsent(fileId, h->new HashMap<>());
	}
	
	@Override
	public FileVersion requestFileVersion(FileVersionId fileVersionId) throws FileManagerException {
		Map<FileVersionId, FileVersion> versionCache = getVersionMap(fileVersionId.getFileId());
		synchronized(versionCache) {
			FileVersion fileVersion = versionCache.get(fileVersionId);
			if(fileVersion == null) {
				if(fileProvider != null) {
					File container = getContainerFolder(fileVersionId);
					
					long t1 = System.currentTimeMillis();
					fileVersion = fileProvider.saveFileVersionTo(fileVersionId, container);
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
				return fileVersion;
			}
		}
	}
	
	protected File getFileCacheFolder(String fileId) {
		return new File(cacheFolder+"/"+fileId);
	}
	
	protected File getFileVersionCacheFolder(FileVersionId fileVersionId) {
		return new File(getFileCacheFolder(fileVersionId.fileId)+"/"+fileVersionId.version+"/");
	}
	
	protected FileVersion storeFile(File source, FileVersionId fileVersionId) throws FileManagerException, IOException {
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

	private File getContainerFolder(FileVersionId fileVersionId) {
		File container = new File(cacheFolder + "/" + fileVersionId.getFileId() + "/" + fileVersionId.getVersion());
		if(!container.exists()) {
			container.mkdirs();
		}
		return container;
	}
	
	private void createMetaFile(File source, FileVersion fileVersion) throws FileManagerException {
		File metaFile = getMetaFile(fileVersion.getVersionId());
		Properties metaProperties = new Properties();
		metaProperties.setProperty(DIRECTORY_PROPERTY, Boolean.toString(fileVersion.isDirectory()));
		if(source!=null) {
			metaProperties.setProperty(ORIGINAL_FILE_PATH_PROPERTY, source.getPath());
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

	@Override
	public void cleanupCache() {
		fileHandleCache.clear();
		fileIdRegistry.clear();
		Arrays.asList(cacheFolder.listFiles()).forEach(f->FileHelper.deleteFolder(f));
	}
}
