package step.grid.filemanager;

import java.io.File;

public interface FileManager extends FileManagerClient {

	/**
	 * Cache the content of the file provided as argument under a specific version for later retrieval
	 * The content of the file is persisted under the version returned. Multiple versions of the same file 
	 * can be registered using this method. If the file changed between registrations a new version will be
	 * returned.
	 * 
	 * @param file the file to be registered
	 * @param deletePreviousVersions if the previous versions of this file should be deleted
	 * @return the {@link FileVersion} of the registered file. The {@link FileVersionId} can be used for later retrival of this version
	 * @throws FileManagerException
	 */
	public FileVersion registerFileVersion(File file, boolean deletePreviousVersions) throws FileManagerException;
	
	/**
	 * Request the specific version of a file.
	 * 
	 * @param fileVersionId the version of the File to be retrieved
	 * @return the {@link FileVersion} corresponding to the version specified or <code>null</code> if the version isn't available
	 * @throws FileManagerException
	 */
	public FileVersion requestFileVersion(FileVersionId fileVersionId) throws FileManagerException;
	
	/**
	 * Removes all cache entries of this cache
	 */
	public void cleanupCache();

}
