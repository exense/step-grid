package step.grid.io.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.agent.events.AgentEventMessage;
import step.grid.agent.events.AgentEventListener;
import step.grid.agent.events.StreamableAttachmentCreatedEventMessage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

// This class provides streamable attachments related functionality.
public class StreamableAttachmentsContext {
    private static final Logger logger = LoggerFactory.getLogger(StreamableAttachmentsContext.class);
    private final Set<AgentEventListener> agentEventListeners = ConcurrentHashMap.newKeySet();
    private final StreamableResourcesUploadClientFactory clientFactory;

    public StreamableAttachmentsContext(StreamableResourcesUploadClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    public StreamableAttachmentsHandlerFactory getHandlerFactory() {
        return new StreamableAttachmentsHandlerFactory(this);
    }

    public StreamableResourceDescriptor createAttachment(String filename, String contentMimeType, InputStream stream, CompletableFuture<TransferStatus> transferStatus, Consumer<Long> uploadCountCallback, String tokenId) throws IOException {
        StreamableResourceDescriptor descriptor;
        try {
            // we can't use try-with-resources here because the client would be immediately closed after starting the upload.
            // Instead, its session will be closed once the transfer is complete, and then the client itself should garbage collected.
            StreamableResourcesUploadClient client = clientFactory.createClient();
            descriptor = client.startUpload(filename, contentMimeType, stream, transferStatus, uploadCountCallback);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
        postAgentEvent(new StreamableAttachmentCreatedEventMessage(tokenId, descriptor));
        return descriptor;
    }

    // If there are more even types in the future, this might be moved to a separate class
    public void addAgentEventListener(AgentEventListener agentEventListener) {
        agentEventListeners.add(agentEventListener);
    }

    public void removeAgentEventListener(AgentEventListener agentEventListener) {
        agentEventListeners.remove(agentEventListener);
    }

    public void postAgentEvent(AgentEventMessage agentEvent) {
        for (AgentEventListener agentEventListener : agentEventListeners) {
            agentEventListener.onAgentEvent(agentEvent);
        }
    }
}
