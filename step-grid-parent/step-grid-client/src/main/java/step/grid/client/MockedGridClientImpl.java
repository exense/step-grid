package step.grid.client;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import step.grid.TokenWrapper;
import step.grid.agent.AgentTokenServices;
import step.grid.agent.handler.MessageHandlerPool;
import step.grid.contextbuilder.ApplicationContextBuilder;
import step.grid.filemanager.FileManagerClient;
import step.grid.filemanager.FileManagerException;
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;
import step.grid.tokenpool.Interest;

public class MockedGridClientImpl extends AbstractGridClientImpl {

	public MockedGridClientImpl() {
		super(new GridClientConfiguration(), new DefaultTokenLifecycleStrategy(), null);
		initLocalAgentServices(); 
		initLocalMessageHandlerPool();
	}

	Map<FileVersionId, FileVersion> fileVersionCache = new HashMap<>();
	
	@Override
	public FileVersion registerFile(File file) throws FileManagerException {
		FileVersionId id = new FileVersionId(UUID.randomUUID().toString(), "");
		FileVersion fileVersion = new FileVersion(file, id, false);
		fileVersionCache.put(id, fileVersion);
		return fileVersion;
	}
	
	protected ConcurrentHashMap<String, FileVersion> resourceCache = new ConcurrentHashMap<>();
	
	@Override
	public FileVersion registerFile(InputStream inputStream, String fileName, boolean isDirectory)
			throws FileManagerException {
		// Ensure the resource is read only once
		FileVersion fileVersion = resourceCache.computeIfAbsent(fileName, f->{
			try {
				File file = File.createTempFile(fileName + "-" + UUID.randomUUID(), fileName.substring(fileName.lastIndexOf(".")));
				Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
				file.deleteOnExit();
				return registerFile(file);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		return fileVersion;
	}

	@Override
	public FileVersion getRegisteredFile(FileVersionId fileVersionId) throws FileManagerException {
		return fileVersionCache.get(fileVersionId);
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
	
	protected void initLocalAgentServices() {
		FileManagerClient fileManagerClient = new FileManagerClient() {
			@Override
			public FileVersion requestFileVersion(FileVersionId fileVersionId) throws FileManagerException {
				return getRegisteredFile(fileVersionId);
			}

			@Override
			public void removeFileVersionFromCache(FileVersionId fileVersionId) {
				unregisterFile(fileVersionId);
			}
		};
		
		localAgentTokenServices = new AgentTokenServices(fileManagerClient);
		localAgentTokenServices.setApplicationContextBuilder(new ApplicationContextBuilder());
	}

	protected void initLocalMessageHandlerPool() {
		localMessageHandlerPool = new MessageHandlerPool(localAgentTokenServices);
	}

}
