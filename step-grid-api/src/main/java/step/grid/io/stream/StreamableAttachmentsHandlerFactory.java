package step.grid.io.stream;

import step.grid.agent.tokenpool.TokenReservationSession;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class StreamableAttachmentsHandlerFactory {
    private final StreamableAttachmentsContext attachmentsContext;

    public StreamableAttachmentsHandlerFactory(StreamableAttachmentsContext attachmentsContext) {
        this.attachmentsContext = attachmentsContext;
    }

    public StreamableAttachmentsHandler create(TokenReservationSession tokenReservationSession) {
        final String tokenId = (String) tokenReservationSession.get(TokenReservationSession.TOKENID_KEY);
        return new StreamableAttachmentsHandler() {
            @Override
            protected StreamableResourceDescriptor handleAttachmentCreation(String filename, String contentMimeType, InputStream stream, CompletableFuture<TransferStatus> transferStatus, Consumer<Long> uploadCountCallback) throws IOException {
                return attachmentsContext.createAttachment(filename, contentMimeType, stream, transferStatus, uploadCountCallback, tokenId);
            }
        };
    }
}
