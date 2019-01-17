package step.grid.filemanager;

import java.io.File;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import step.commons.helpers.FileHelper;

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
	public FileManagerClientImpl(File cacheFolder, FileVersionProvider fileProvider) {
		super(cacheFolder);
		this.fileProvider = fileProvider;
		loadCache();
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
	
	@Override
	public void removeFileVersionFromCache(FileVersionId fileVersionId) {
		Map<FileVersionId, FileVersion> versionCache = getVersionMap(fileVersionId.getFileId());
		synchronized(versionCache) {
			FileVersion fileVersion = versionCache.get(fileVersionId);
			if(fileVersion != null) {
				FileHelper.deleteFolder(getContainerFolder(fileVersionId));
				versionCache.remove(fileVersionId);
			}
		}
	}
}
