package step.grid.agent.events;

import jakarta.websocket.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.io.stream.JsonMessage;

import java.net.URI;
import java.util.Objects;

// This is the Step server side implementation for the Agent events websocket messages.
public class AgentEventsWebsocketClient {
    private static final Logger logger = LoggerFactory.getLogger(AgentEventsWebsocketClient.class);
    private final URI websocketUri;
    private final AgentEventsWebsocketClient client = this;
    private final GridAgentEventsBroker broker;

    private final Session session;

    public AgentEventsWebsocketClient(GridAgentEventsBroker broker, String agentBaseUri) throws Exception {
        this.broker = Objects.requireNonNull(broker);
        this.websocketUri = URI.create(Objects.requireNonNull(agentBaseUri).replaceFirst("^http", "ws") + "/ws/events");
        this.session = connect();
    }

    private void onMessage(AgentEventMessage event) {
        logger.info("onMessage: {}", event);
        broker.onAgentEvent(event);
    }

    private void onClose(CloseReason reason) {
        logger.info("onClose: {}", reason);
    }

    private void onError(Throwable throwable) {
        logger.error("onError", throwable);
    }

    public void close() {
        try {
            if (session.isOpen()) {
                session.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "Server-side session close"));
            }
        } catch (Exception e) {
            logger.error("unexpected error while closing session", e);
        }
    }

    private Session connect() throws Exception {
        return ContainerProvider.getWebSocketContainer().connectToServer(new Remote(), ClientEndpointConfig.Builder.create().build(), websocketUri);
    }

    private class Remote extends Endpoint {

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            // No timeout; the other side of the connection will send keepalive messages
            session.setMaxIdleTimeout(0);
            session.addMessageHandler(String.class, message -> client.onMessage(JsonMessage.fromString(message)));
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            client.onClose(closeReason);
        }

        @Override
        public void onError(Session session, Throwable throwable) {
            client.onError(throwable);
        }
    }

}
