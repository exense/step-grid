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
import java.nio.file.Files;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import ch.exense.commons.io.FileHelper;
import step.grid.filemanager.FileManagerImpl.FileManagerImplConfig;

public class FileManagerImplTest {

	protected File registryFolder;
	protected FileManagerImpl f;
	
	@Before
	public void before() throws IOException {
		registryFolder = FileHelper.createTempFolder();
		FileManagerImplConfig config = new FileManagerImplConfig();
		// disable caching
		config.setFileLastModificationCacheExpireAfter(0);
		f = new FileManagerImpl(registryFolder, config);
	}
	
	@After
	public void after() throws IOException {
		FileHelper.deleteFolder(registryFolder);
	}
	
	@Test
	public void testFile() throws IOException, FileManagerException {
		File testFile = FileHelper.createTempFile();
		
		FileVersionId handle = f.registerFileVersion(testFile, false).getVersionId();
		
		FileVersion registeredFile = f.getFileVersion(handle);
		Assert.assertNotNull(registeredFile);
		
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
	
	@Test
	public void testStream() throws IOException, FileManagerException {
		FileVersionId handle = f.registerFileVersion(this.getClass().getResourceAsStream("TestFile.txt"),"TestFile", false, false).getVersionId();;
		
		FileVersion fileVersion = f.getFileVersion(handle);
		FileVersion registeredFile = fileVersion;
		Assert.assertNotNull(registeredFile);
		
		// register a different content under the same name "TestFile"
		FileVersionId handle2 = f.registerFileVersion(this.getClass().getResourceAsStream("TestFile2.txt"),"TestFile", false, false).getVersionId();
		
		// register the same content under a different name
		FileVersionId handle3 = f.registerFileVersion(this.getClass().getResourceAsStream("TestFile.txt"),"AnotherResourceName", false, false).getVersionId();;
		
		Assert.assertFalse(handle2.equals(handle));
		Assert.assertFalse(handle3.equals(handle));
		
		FileVersion registeredFileHandle1 = fileVersion;
		FileVersion registeredFileHandle2 = f.getFileVersion(handle2);
		FileVersion registeredFileHandle3 = f.getFileVersion(handle3);
		Assert.assertNotNull(registeredFileHandle2);
		Assert.assertFalse(registeredFileHandle2.isDirectory());
		Assert.assertEquals("Dummy content 2",Files.readAllLines(registeredFileHandle2.getFile().toPath()).get(0));
		
		Assert.assertNotNull(registeredFileHandle3);
		Assert.assertFalse(registeredFileHandle3.isDirectory());
		Assert.assertEquals("Dummy content 1",Files.readAllLines(registeredFileHandle3.getFile().toPath()).get(0));
		
		Assert.assertEquals(registeredFileHandle1, registeredFile);
		Assert.assertFalse(registeredFileHandle1.equals(registeredFileHandle2));
		
		f.unregisterFileVersion(handle);
		registeredFileHandle1 = f.getFileVersion(handle);
		Assert.assertNull(registeredFileHandle1);
	}
	
	@Test
	public void testMultipleVersions() throws IOException, FileManagerException {
		// Create a single empty file
		File testFile = FileHelper.createTempFile();
		
		// Register a first version of the file
		FileVersionId version1 = f.registerFileVersion(testFile, false).getVersionId();
		
		// Register a second version of the file
		writeFileContent(testFile, "V2");
		FileVersionId version2 = f.registerFileVersion(testFile, false).getVersionId();
		
		// Delete the source file. As soon as registered, it shouldn't be needed anymore
		testFile.delete();
		
		// Assert the first version is still available
		FileVersion fileVersion1 = f.getFileVersion(version1);
		Assert.assertEquals("", new String(Files.readAllBytes(fileVersion1.getFile().toPath())));
		
		// Retrieve the version 2
		FileVersion fileVersion2 = f.getFileVersion(version2);
		Assert.assertEquals("V2", new String(Files.readAllBytes(fileVersion2.getFile().toPath())));
		
		// Register a third version of the file and DELETE the other versions
		writeFileContent(testFile, "V3");
		FileVersionId version3 = f.registerFileVersion(testFile, true).getVersionId();
		
		FileVersion fileVersion3 = f.getFileVersion(version3);
		Assert.assertEquals("V3", new String(Files.readAllBytes(fileVersion3.getFile().toPath())));

		// the old versions should have been deleted
		Assert.assertNull(f.getFileVersion(version1));
		Assert.assertNull(f.getFileVersion(version2));
	}

	@Test
	public void testUnregistration() throws IOException, FileManagerException {
		// Create a single empty file
		File testFile = FileHelper.createTempFile();
		
		// Register a first version of the file
		FileVersionId version1 = f.registerFileVersion(testFile, false).getVersionId();
		
		// Register the same file a second time
		FileVersionId version2 = f.registerFileVersion(testFile, false).getVersionId();
		
		// Delete the source file. As soon as registered, it shouldn't be needed anymore
		testFile.delete();
		
		Assert.assertEquals(version1, version2);
		
		// Request deletion of the the file version
		f.unregisterFileVersion(version1);
			
		// The file is still not available anymore
		Assert.assertNull(f.getFileVersion(version1));
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

	
	@Test
	public void testDirectory() throws IOException, FileManagerException {
		File testFile = FileHelper.createTempFile();
		
		FileVersionId handle = f.registerFileVersion(testFile, false).getVersionId();
		
		FileVersion file = f.getFileVersion(handle);
		Assert.assertNotNull(file);
	}
	
	@Test
	public void testCleanup() throws IOException, FileManagerException {
		File testFile = FileHelper.createTempFile();
		File testFile2 = FileHelper.createTempFile();
		
		f.registerFileVersion(testFile, false);
		f.registerFileVersion(testFile2, false);
		
		Assert.assertEquals(2, registryFolder.list().length);
		f.cleanupCache();
		Assert.assertEquals(0, registryFolder.list().length);
	}
	
	@Test
	public void testCacheReload() throws IOException, FileManagerException {
		File testFile = FileHelper.createTempFile();
		File testFolder = FileHelper.createTempFolder();
		
		FileVersion fileVersion1 = f.registerFileVersion(testFile, false);
		FileVersion fileVersion2 = f.registerFileVersion(testFolder, false);
		
		f = new FileManagerImpl(registryFolder);
		FileVersion fileVersionActual1 = f.getFileVersion(fileVersion1.getVersionId());
		FileVersion fileVersionActual2 = f.getFileVersion(fileVersion2.getVersionId());
		
		Assert.assertEquals(fileVersion1, fileVersionActual1);
		Assert.assertEquals(fileVersion2, fileVersionActual2);
	}
	
	
}
