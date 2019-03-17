package step.grid.client;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import step.grid.TokenWrapper;
import step.grid.agent.AgentTokenServices;
import step.grid.agent.handler.MessageHandlerPool;
import step.grid.client.AbstractGridClientImpl;
import step.grid.client.DefaultTokenLifecycleStrategy;
import step.grid.client.GridClientConfiguration;
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

	@Override
	public FileVersion getRegisteredFile(FileVersionId fileVersionId) throws FileManagerException {
		return fileVersionCache.get(fileVersionId);
	}

	@Override
	public void unregisterFile(FileVersionId fileVersionId) {
		fileVersionCache.remove(fileVersionId);
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
