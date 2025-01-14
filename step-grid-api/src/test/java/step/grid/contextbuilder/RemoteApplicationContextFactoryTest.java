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
package step.grid.contextbuilder;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import step.grid.filemanager.FileManagerClient;
import step.grid.filemanager.FileManagerException;
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;

public class RemoteApplicationContextFactoryTest {

	public static class TestFileManagerClient implements FileManagerClient {

		public final AtomicInteger usage = new AtomicInteger(0);
		@Override
		public FileVersion requestFileVersion(FileVersionId fileVersionId, boolean cleanable) throws FileManagerException {
			usage.incrementAndGet();
			return new FileVersion(new File(""), fileVersionId, false);
		}

		@Override
		public void removeFileVersionFromCache(FileVersionId fileVersionId) {

		}

		@Override
		public void cleanupCache() {

		}

		@Override
		public void releaseFileVersion(FileVersion fileVersion) {
			usage.decrementAndGet();
		}

		@Override
		public void close() throws Exception {

		}
	};

	@Test
	public void test() throws FileManagerException, IOException {
		final FileVersionId fileVersionId = new FileVersionId("id1", "1");
		TestFileManagerClient fileManagerClient = new TestFileManagerClient();
		RemoteApplicationContextFactory f1 = new RemoteApplicationContextFactory(fileManagerClient, fileVersionId, true);
		ClassLoader cl = f1.buildClassLoader(this.getClass().getClassLoader());
		Assert.assertNotNull(cl);
		Assert.assertEquals(1, fileManagerClient.usage.get());
		Assert.assertTrue(cl instanceof JavaLibrariesClassLoader);
		((JavaLibrariesClassLoader) cl).close();
		f1.onClassLoaderClosed();
		Assert.assertEquals(0, fileManagerClient.usage.get());
		
		// Same ID but different version
		final FileVersionId fileVersionId2 = new FileVersionId("id1", "2");
		RemoteApplicationContextFactory f2 = new RemoteApplicationContextFactory(fileManagerClient, fileVersionId2, true);
		
		Assert.assertNotSame(f2.getId(), f1.getId());
		
		// Different ID but same version
		final FileVersionId fileVersionId3 = new FileVersionId("id2", "1");
		RemoteApplicationContextFactory f3 = new RemoteApplicationContextFactory(fileManagerClient, fileVersionId3, true);
		
		Assert.assertNotSame(f3.getId(), f1.getId());
		
	}

}
