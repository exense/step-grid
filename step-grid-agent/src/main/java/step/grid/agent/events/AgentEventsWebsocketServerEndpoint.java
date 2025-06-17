package step.grid.agent.events;

import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.io.WebsocketServerEndpointSessionsHandler;
import step.grid.io.stream.StreamableAttachmentsContext;

// Websocket endpoint where agent events are emitted.
// The step server will open a connection to this endpoint when the agent is registered.
// Currently, the only type of events that is sent is about streaming attachment creation.
public class AgentEventsWebsocketServerEndpoint extends Endpoint implements AgentEventListener {
    public static ServerEndpointConfig getEndpointConfig(StreamableAttachmentsContext streamableAttachmentsContext) {
        return ServerEndpointConfig.Builder.create(
                        AgentEventsWebsocketServerEndpoint.class, "/ws/events"
                ).configurator(new ServerEndpointConfig.Configurator() {
                    @Override
                    public <K> K getEndpointInstance(Class<K> endpointClass) {
                        return endpointClass.cast(new AgentEventsWebsocketServerEndpoint(streamableAttachmentsContext));
                    }
                })
                .build();
    }

    private static final Logger logger = LoggerFactory.getLogger(AgentEventsWebsocketServerEndpoint.class);

    private final StreamableAttachmentsContext attachmentsContext;

    public AgentEventsWebsocketServerEndpoint(StreamableAttachmentsContext streamableAttachmentsContext) {
        this.attachmentsContext = streamableAttachmentsContext;
    }

    private Session session;

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        this.session = session;
        attachmentsContext.addAgentEventListener(this);
        WebsocketServerEndpointSessionsHandler.getInstance().register(session);
        logger.info("Session opened: {}", session.getId());
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        logger.info("Session closed: {}, reason: {}", session.getId(), closeReason);
        attachmentsContext.removeAgentEventListener(this);
        WebsocketServerEndpointSessionsHandler.getInstance().unregister(session);
    }

    @Override
    public void onError(Session session, Throwable t) {
        logger.error("Exception in Session {}: {}", session.getId(), t.getMessage(), t);
    }

    @Override
    public void onAgentEvent(AgentEventMessage agentEvent) {
        session.getAsyncRemote().sendText(agentEvent.toString());
    }
}
