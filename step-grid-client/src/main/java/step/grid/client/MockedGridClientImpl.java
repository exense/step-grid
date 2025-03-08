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
package step.grid.client;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import step.grid.TokenWrapper;
import step.grid.agent.AgentTokenServices;
import step.grid.contextbuilder.ApplicationContextBuilder;
import step.grid.filemanager.FileManagerClient;
import step.grid.filemanager.FileManagerException;
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;
import step.grid.tokenpool.Interest;

public class MockedGridClientImpl extends AbstractGridClientImpl {

	public Map<String, TestCacheVersion> cacheUsage = new ConcurrentHashMap<>();
	public FileManagerClient fileManagerClient;

	public MockedGridClientImpl() {
		super(new GridClientConfiguration(), new DefaultTokenLifecycleStrategy(), null);
		initLocalAgentServices(); 
		initLocalMessageHandlerPool();
	}

	Map<FileVersionId, FileVersion> fileVersionCache = new HashMap<>();
	
	@Override
	public FileVersion registerFile(File file, boolean cleanable) throws FileManagerException {
		FileVersionId id = new FileVersionId(UUID.randomUUID().toString(), "");
		FileVersion fileVersion = new FileVersion(file, id, false);
		fileVersionCache.put(id, fileVersion);
		cacheUsage.computeIfAbsent(fileVersion.getFile().getAbsolutePath(), (i) -> new TestCacheVersion(cleanable)).usageCount.incrementAndGet();
		return fileVersion;
	}
	
	protected ConcurrentHashMap<String, FileVersion> resourceCache = new ConcurrentHashMap<>();
	
	@Override
	public FileVersion registerFile(InputStream inputStream, String fileName, boolean isDirectory, boolean cleanable)
			throws FileManagerException {
		// Ensure the resource is read only once
		FileVersion fileVersion = resourceCache.computeIfAbsent(fileName, f->{
			try {
				File file = File.createTempFile(fileName + "-" + UUID.randomUUID(), fileName.substring(fileName.lastIndexOf(".")));
				Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
				file.deleteOnExit();
				return registerFile(file, cleanable);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		return fileVersion;
	}

	@Override
	public void releaseFile(FileVersion fileVersion) {
		cacheUsage.get(fileVersion.getFile().getAbsolutePath()).usageCount.decrementAndGet();
	}

	@Override
	public FileVersion getRegisteredFile(FileVersionId fileVersionId) throws FileManagerException {
		FileVersion fileVersion = fileVersionCache.get(fileVersionId);
		cacheUsage.computeIfAbsent(fileVersion.getFile().getAbsolutePath(), (i) -> new TestCacheVersion(true)).usageCount.incrementAndGet();
		return fileVersion;
	}

	@Override
	public void unregisterFile(FileVersionId fileVersionId) {
		fileVersionCache.remove(fileVersionId);
		resourceCache.entrySet().removeIf(k->{
			return k.getValue().getVersionId().equals(fileVersionId);
		});
	}

	@Override
	public TokenWrapper getTokenHandle(Map<String, String> attributes, Map<String, Interest> interests,
			boolean createSession) throws AgentCommunicationException {
		throw new RuntimeException("Not supported");
	}

	public static class TestCacheVersion {
		public final AtomicInteger usageCount = new AtomicInteger(0);
		public final boolean cleanable;

		public TestCacheVersion(boolean cleanable) {
			this.cleanable = cleanable;
		}
	}

	public class MockedFileManagerClient implements FileManagerClient {
		public Map<String, TestCacheVersion> clientCacheUsage = new ConcurrentHashMap<>();
		@Override
		public FileVersion requestFileVersion(FileVersionId fileVersionId, boolean cleanableFromClientCache) throws FileManagerException {
			FileVersion registeredFile = getRegisteredFile(fileVersionId);
			clientCacheUsage.computeIfAbsent(registeredFile.getFile().getAbsolutePath(), (c) -> new TestCacheVersion(cleanableFromClientCache)).usageCount.incrementAndGet();
			return registeredFile;
		}

		@Override
		public void removeFileVersionFromCache(FileVersionId fileVersionId) {
			unregisterFile(fileVersionId);
		}

		@Override
		public void cleanupCache() {

		}

		@Override
		public void releaseFileVersion(FileVersion fileVersion) {
			releaseFile(fileVersion);
			clientCacheUsage.get(fileVersion.getFile().getAbsolutePath()).usageCount.decrementAndGet();
		}

		@Override
		public void close() throws Exception {

		}
	}

	@Override
	protected void initLocalAgentServices() {
		fileManagerClient = new MockedFileManagerClient();
		
		localAgentTokenServices = new AgentTokenServices(fileManagerClient);
		applicationContextBuilder = new ApplicationContextBuilder(this.gridClientConfiguration.getLocalTokenApplicationContextConfiguration());
		localAgentTokenServices.setApplicationContextBuilder(applicationContextBuilder);
	}

}
