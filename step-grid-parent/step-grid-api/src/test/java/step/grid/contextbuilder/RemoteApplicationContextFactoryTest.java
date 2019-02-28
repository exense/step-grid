package step.grid.contextbuilder;

import java.io.File;

import org.junit.Test;

import junit.framework.Assert;
import step.grid.filemanager.FileManagerClient;
import step.grid.filemanager.FileManagerException;
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;

public class RemoteApplicationContextFactoryTest {

	@Test
	public void test() throws FileManagerException {
		final FileVersionId fileVersionId = new FileVersionId("id1", 1);
		FileManagerClient fileManagerClient = new FileManagerClient() {
			
			@Override
			public FileVersion requestFileVersion(FileVersionId fileVersionId) throws FileManagerException {
				return new FileVersion(new File(""), fileVersionId, false);
			}
		};
		RemoteApplicationContextFactory f1 = new RemoteApplicationContextFactory(fileManagerClient, fileVersionId);
		ClassLoader cl = f1.buildClassLoader(this.getClass().getClassLoader());
		Assert.assertNotNull(cl);
		
		// Same ID but different version
		final FileVersionId fileVersionId2 = new FileVersionId("id1", 2);
		RemoteApplicationContextFactory f2 = new RemoteApplicationContextFactory(fileManagerClient, fileVersionId2);
		
		Assert.assertNotSame(f2.getId(), f1.getId());
		
		// Different ID but same version
		final FileVersionId fileVersionId3 = new FileVersionId("id2", 1);
		RemoteApplicationContextFactory f3 = new RemoteApplicationContextFactory(fileManagerClient, fileVersionId3);
		
		Assert.assertNotSame(f3.getId(), f1.getId());
		
	}

}
