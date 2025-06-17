package step.grid.agent.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.AgentRef;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import java.util.function.Consumer;

// This class brokers agent events.
public class GridAgentEventsBroker implements AgentEventListener {
    private static final Logger logger = LoggerFactory.getLogger(GridAgentEventsBroker.class);

    private final Map<String, AgentEventsWebsocketClient> agentUrls = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<Consumer<AgentEventMessage>>> callbacksByTokenId = new ConcurrentHashMap<>();

    private boolean shuttingDown = false;

    public void unregisterAgents(List<AgentRef> expired) {
        for (AgentRef agentRef : expired) {
            logger.info("Unregistering agent id={} url={} type={}", agentRef.getAgentId(), agentRef.getAgentUrl(), agentRef.getAgentType());
            unregisterAgent(agentRef.getAgentUrl());
        }
    }

    private void unregisterAgent(String agentUrl) {
        AgentEventsWebsocketClient client = agentUrls.remove(agentUrl);
        if (client != null) {
            client.close();
        }
    }

    public void close() {
        logger.info("Shutting down");
        shuttingDown = true;
        for (String agentUrl: new ArrayList<>(agentUrls.keySet())) {
            unregisterAgent(agentUrl);
        }
    }

    public void registerAgent(AgentRef agentRef) {
        if (!shuttingDown) {
            if (!agentUrls.containsKey(agentRef.getAgentUrl())) {
                logger.info("Registering agent id={} url={} type={}", agentRef.getAgentId(), agentRef.getAgentUrl(), agentRef.getAgentType());
                try {
                    AgentEventsWebsocketClient client = new AgentEventsWebsocketClient(this, agentRef.getAgentUrl());
                    agentUrls.put(agentRef.getAgentUrl(), client);
                } catch (Exception e) {
                    logger.error("Unable to create events websocket client, probably not a supported agent type", e);
                }
            }
        } else {
            logger.info("Skipping agent registration for agent={}, shutdown is in progress", agentRef.getAgentUrl());
        }
    }

    @Override
    public void onAgentEvent(AgentEventMessage agentEvent) {
        logger.info("onAgentEvent: {}", agentEvent);
        List<Consumer<AgentEventMessage>> callbacks = callbacksByTokenId.get(agentEvent.agentToken);
        if (callbacks != null) {
            for (Consumer<AgentEventMessage> callback : callbacks) {
                callback.accept(agentEvent);
            }
        }
    }

    public void registerCallback(String tokenId, Consumer<AgentEventMessage> callback) {
        callbacksByTokenId.computeIfAbsent(tokenId, k -> new CopyOnWriteArrayList<>()).add(callback);
    }

    public void unregisterCallback(String tokenId, Consumer<AgentEventMessage> callback) {
        Optional.ofNullable(callbacksByTokenId.get(tokenId)).ifPresent(list -> {
            list.removeIf(cb -> cb == callback);
            if (list.isEmpty()) {
                callbacksByTokenId.remove(tokenId);
            }
        });
    }
}
