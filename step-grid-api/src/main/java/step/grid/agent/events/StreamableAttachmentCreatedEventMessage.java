package step.grid.agent.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import step.grid.io.stream.StreamableResourceDescriptor;

import java.time.Instant;

public class StreamableAttachmentCreatedEventMessage extends AgentEventMessage {
    public StreamableResourceDescriptor attachmentDescriptor;

    @JsonCreator
    public StreamableAttachmentCreatedEventMessage(
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("agentToken") String agentToken,
            @JsonProperty("attachmentDescriptor") StreamableResourceDescriptor attachmentDescriptor) {
        super(timestamp, agentToken);
        this.attachmentDescriptor = attachmentDescriptor;
    }

    public StreamableAttachmentCreatedEventMessage(String agentToken, StreamableResourceDescriptor attachmentDescriptor) {
        this(Instant.now(), agentToken, attachmentDescriptor);
    }
}
