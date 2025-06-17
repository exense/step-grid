package step.grid.agent.events;

import step.grid.io.stream.JsonMessage;

import java.time.Instant;

public class AgentEventMessage extends JsonMessage {

    public final Instant timestamp;
    public final String agentToken;


    public AgentEventMessage(Instant timestamp, String agentToken) {
        this.timestamp = timestamp;
        this.agentToken = agentToken;
    }

}
