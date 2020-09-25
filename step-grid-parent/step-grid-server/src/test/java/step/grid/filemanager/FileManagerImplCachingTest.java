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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import ch.exense.commons.io.FileHelper;
import step.grid.filemanager.FileManagerImpl.FileManagerImplConfig;

public class FileManagerImplCachingTest {

	protected File registryFolder;
	protected FileManagerImpl f;
	
	@Before
	public void before() throws IOException {
		registryFolder = FileHelper.createTempFolder();
		FileManagerImplConfig config = new FileManagerImplConfig();
		// configure the expiration duration of the file modification cache of the FileManager to 10ms
		config.setFileLastModificationCacheExpireAfter(10);
		f = new FileManagerImpl(registryFolder, config);
	}
	
	@After
	public void after() throws IOException {
		FileHelper.deleteFolder(registryFolder);
	}
	
	
	@Test
	public void testFileManagerCaching() throws IOException, FileManagerException, InterruptedException {
		File testFile = FileHelper.createTempFile();
		
		FileVersionId handle = f.registerFileVersion(testFile, false).getVersionId();
		
		FileVersion registeredFile = f.getFileVersion(handle);
		Assert.assertNotNull(registeredFile);
		
		// wait 100ms for the file modification cache entries of the FileManager to expire
		Thread.sleep(100);
		writeFileContent(testFile, "V2");

		FileVersionId handle2 = f.registerFileVersion(testFile, false).getVersionId();
		// Registering twice (this shouldn't throw any error)
		handle2 = f.registerFileVersion(testFile, false).getVersionId();
		
		Assert.assertFalse(handle2.equals(handle));
		
		FileVersion registeredFileHandle1 = f.getFileVersion(handle);
		FileVersion registeredFileHandle2 = f.getFileVersion(handle2);
		Assert.assertNotNull(registeredFileHandle2);
		
		Assert.assertEquals(registeredFileHandle1, registeredFile);
		Assert.assertFalse(registeredFileHandle1.equals(registeredFileHandle2));
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
