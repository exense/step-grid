package step.grid.io.stream.upload;

import jakarta.websocket.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.io.stream.*;
import step.grid.io.stream.data.CheckpointingOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;

// This is the client class to actually send streamable resource uploads via Websocket.
public class StreamableResourcesWebsocketUploadClient implements StreamableResourcesUploadClient {
    private static final int MAX_CONCURRENT_UPLOADS = 100;
    private static final Logger logger = LoggerFactory.getLogger(StreamableResourcesWebsocketUploadClient.class);
    private static final ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_UPLOADS);

    public static class Factory implements StreamableResourcesUploadClientFactory {
        private final URI endpointUri;
        private final WebSocketContainer webSocketContainer;

        public Factory(URI endpointUri) {
            this.endpointUri = endpointUri;
            // we need to initialize this here, as it may not be found from custom classloaders later
            this.webSocketContainer = ContainerProvider.getWebSocketContainer();
        }

        @Override
        public StreamableResourcesUploadClient createClient() throws IOException {
            return new StreamableResourcesWebsocketUploadClient(endpointUri, webSocketContainer);
        }
    }

    private enum State {
        INITIAL,
        EXPECTING_DESCRIPTOR,
        UPLOADING,
    }

    private final StreamableResourcesWebsocketUploadClient self = this;
    private final Session session;
    private State state = State.INITIAL;
    private String filename;
    private InputStream inputStream;
    private CompletableFuture<TransferStatus> transferStatus;
    private Consumer<Long> uploadCountCallback;
    private CompletableFuture<StreamableResourceDescriptor> attachmentDescriptorFuture;

    public StreamableResourcesWebsocketUploadClient(URI endpointUri, WebSocketContainer container) throws IOException {
        session = connect(container, endpointUri);
    }

    @Override
    public String toString() {
        return String.format("%s[session=%s,state=%s,filename=%s]", this.getClass().getSimpleName(), session.getId(), state, filename);
    }

    private Session connect(WebSocketContainer container, URI websocketUri) throws IOException {
        try {
            return container.connectToServer(new Remote(), ClientEndpointConfig.Builder.create().build(), websocketUri);
        } catch (DeploymentException e) {
            throw new IOException(e);
        }
    }

    @Override
    public StreamableResourceDescriptor startUpload(String filename, String contentMimeType, InputStream stream, CompletableFuture<TransferStatus> transferStatus, Consumer<Long> uploadCountCallback) throws IOException {
        this.filename = Objects.requireNonNull(filename);
        this.inputStream = Objects.requireNonNull(stream);
        this.transferStatus = Objects.requireNonNull(transferStatus);
        this.uploadCountCallback = uploadCountCallback;
        logger.info("Uploading attachment {}", filename);
        attachmentDescriptorFuture = new CompletableFuture<>();
        state = State.EXPECTING_DESCRIPTOR;
        session.getAsyncRemote().sendText(new RequestUploadMessage(filename, contentMimeType).toString(), result -> {
            if (!result.isOK()) {
                attachmentDescriptorFuture.completeExceptionally(result.getException());
            }
        });
        try {
            logger.info("{}: Awaiting server reply for {}", this, filename);
            return attachmentDescriptorFuture.get();
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private void onMessage(UploadProtocolMessage message) {
        boolean handled = false;
        if (state == State.EXPECTING_DESCRIPTOR) {
            if (message instanceof ReadyForUploadMessage) {
                attachmentDescriptorFuture.complete(((ReadyForUploadMessage) message).attachmentDescriptor);
                state = State.UPLOADING;
                performAsynchronousUpload();
            } else {
                attachmentDescriptorFuture.completeExceptionally(new IllegalStateException("Unexpected message: " + message));
            }
            handled = true;
        }
        if (!handled) {
            throw new IllegalStateException(String.format("Unexpected message: %s", message));
        }
    }

    private void performAsynchronousUpload() {
        CompletableFuture<Long> transferred = submitToCompletable(this::performUpload);
        transferred.handle((bytes,exception) -> {
            if (exception != null) {
                transferStatus.complete(TransferStatus.FAILED.setDetails(exception.getMessage()));
            } else {
                transferStatus.complete(TransferStatus.COMPLETED.setTransferredBytes(bytes));
            }
            return null;
        });
    }

    public static <T> CompletableFuture<T> submitToCompletable(Callable<T> task) {
        CompletableFuture<T> cf = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                cf.complete(task.call());
            } catch (Throwable t) {
                cf.completeExceptionally(t);
            }
        });
        return cf;
    }

    private long performUpload() throws IOException {
        logger.info("Streaming attachment {}", filename);
        OutputStream output = session.getBasicRemote().getSendStream();
        CheckpointingOutputStream flushingOut = new CheckpointingOutputStream(output, CheckpointingOutputStream.DEFAULT_FLUSH_INTERVAL_MILLIS, uploadCountCallback);
        long total = inputStream.transferTo(flushingOut);
        inputStream.close();
        flushingOut.close();
        return total;
    }


    // This may be invoked after an exception, or on normal closure by the endpoint.
    // We do all cleanup here.
    void onClose(CloseReason reason) {
        // session is closed at this point.
        logger.info("{}: Closing session, reason={}", this, reason);
        if (!attachmentDescriptorFuture.isDone()) {
            attachmentDescriptorFuture.completeExceptionally(new IllegalStateException("Unexpected close: " + this));
        }
        if (!transferStatus.isDone()) {
            transferStatus.complete(TransferStatus.FAILED.setDetails("Unexpected close"));
        }
    }

    // This will be followed by an abnormal closure (framework takes care of this)
    void onError(Throwable throwable) {
        logger.error("{}: {}", this, throwable.getMessage(), throwable);
    }

    private class Remote extends Endpoint {

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            // No timeout; server side takes care of keepalive messages
            session.setMaxIdleTimeout(0);
            session.addMessageHandler(String.class, message -> self.onMessage(JsonMessage.fromString(message)));
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            self.onClose(closeReason);
        }

        @Override
        public void onError(Session session, Throwable throwable) {
            self.onError(throwable);
        }
    }

}
