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
							String versionStr = container.getName();
							if(container.isDirectory()) {
								long version = Long.parseLong(versionStr);
								FileVersionId fileVersionId = new FileVersionId(fileId, version);
								
								Properties metaProperties = getMetaProperties(fileVersionId);
								boolean isDirectory = Boolean.parseBoolean(metaProperties.getProperty(DIRECTORY_PROPERTY));
								String originalFilePath = metaProperties.getProperty(ORIGINAL_FILE_PATH_PROPERTY);
								
								if(originalFilePath != null) {
									File originalFile = new File(originalFilePath);
									onFileLoad(originalFile, fileId);
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
	
	protected void onFileLoad(File file, String fileId) {
		
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
	
	protected void createMetaFile(File source, FileVersion fileVersion) throws FileManagerException {
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
}