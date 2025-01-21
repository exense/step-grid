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
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import ch.exense.commons.io.FileHelper;

import static org.junit.Assert.assertNull;

public class FileManagerImplCachingTest {

	protected File registryFolder;
	protected FileManagerImpl fileManager;
	
	@Before
	public void before() throws IOException {
		registryFolder = FileHelper.createTempFolder();
		FileManagerImplConfig config = new FileManagerImplConfig();
		// configure the expiration duration of the file modification cache of the FileManager to 10ms
		config.setFileLastModificationCacheExpireAfter(10);
		config.setConfigurationTimeUnit(TimeUnit.MILLISECONDS);
		config.setCleanupLastAccessTimeThresholdMinutes(10);
		fileManager = new FileManagerImpl(registryFolder, config);
	}
	
	@After
	public void after() throws IOException {
		FileHelper.deleteFolder(registryFolder);
	}
	
	
	@Test
	public void testFileManagerCaching() throws IOException, FileManagerException, InterruptedException {
		File testFile = FileHelper.createTempFile();

		FileVersion firstRegisteredVersion = fileManager.registerFileVersion(testFile, false, true);
		FileVersionId handle = firstRegisteredVersion.getVersionId();
		
		FileVersion registeredFileHandle = fileManager.getFileVersion(handle);
		Assert.assertNotNull(registeredFileHandle);
		
		// wait 100ms for the file modification cache entries of the FileManager to expire
		Thread.sleep(100);
		writeFileContent(testFile, "V2");

		FileVersion secondFileVersion = fileManager.registerFileVersion(testFile, false, true);
		// Registering twice (this shouldn't throw any error) --> but need to be released twice
		secondFileVersion = fileManager.registerFileVersion(testFile, false, true);
		FileVersionId handle2 = secondFileVersion.getVersionId();
		
		Assert.assertFalse(handle2.equals(handle));
		
		FileVersion registeredFileHandle1 = fileManager.getFileVersion(handle);
		FileVersion registeredFileHandle2 = fileManager.getFileVersion(handle2);
		Assert.assertNotNull(registeredFileHandle2);
		
		Assert.assertEquals(registeredFileHandle1, registeredFileHandle);
		Assert.assertFalse(registeredFileHandle1.equals(registeredFileHandle2));

		fileManager.releaseFileVersion(firstRegisteredVersion);
		fileManager.releaseFileVersion(registeredFileHandle);
		fileManager.releaseFileVersion(secondFileVersion);
		fileManager.releaseFileVersion(secondFileVersion); // 2nd registration
		fileManager.releaseFileVersion(registeredFileHandle1);
		fileManager.releaseFileVersion(registeredFileHandle2);

		// wait 100ms for the file last access time TTL to take effect
		Thread.sleep(100);
		fileManager.cleanupCache();
		assertNull(fileManager.getFileVersion(handle));
		assertNull(fileManager.getFileVersion(handle2));
	}
	
	private void writeFileContent(File testFile, String content) throws IOException {
		// Sleep 100ms to ensure that the file lastmodification's date get updated
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {}
		
		try(FileWriter w = new FileWriter(testFile)) {
			w.write(content);
		}
	}
}
