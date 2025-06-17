package step.grid.websockets;

import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// TODO: Note: this class is duplicated in step-grid-server and step-grid-agent
// because there is no common project yet which has the necessary
// dependencies to websockets and could be reused across both.
// Somewhat counterintuitively, the step-grid-server-common project is not that common :-D

// This will automatically set a timeout of 0, and add periodic keepalive
// messages, to all registered sessions. In addition, all sessions will
// be closed on shutdown.
public class WebsocketServerEndpointSessionsHandler {
    private static volatile WebsocketServerEndpointSessionsHandler INSTANCE;
    private static final Logger logger = LoggerFactory.getLogger(WebsocketServerEndpointSessionsHandler.class);

    private final ScheduledExecutorService scheduler;
    private final Set<Session> sessions = ConcurrentHashMap.newKeySet();
    private final ByteBuffer pingPayload = ByteBuffer.wrap(new byte[]{42});
    private static final long PING_INTERVAL_SECONDS = 25;

    private WebsocketServerEndpointSessionsHandler() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, this.getClass().getSimpleName());
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::sendPings, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public static WebsocketServerEndpointSessionsHandler getInstance() {
        if (INSTANCE == null) {
            synchronized (WebsocketServerEndpointSessionsHandler.class) {
                if (INSTANCE == null) {
                    logger.info("Instantiating and starting {}", WebsocketServerEndpointSessionsHandler.class.getSimpleName());
                    INSTANCE = new WebsocketServerEndpointSessionsHandler();
                }
            }
        }
        return INSTANCE;
    }

    public void register(Session session) {
        session.setMaxIdleTimeout(0);
        sessions.add(Objects.requireNonNull(session));
    }

    public void unregister(Session session) {
        if (session != null) {
            sessions.remove(session);
        }
    }

    private void sendPings() {
        for (Session session : sessions) {
            if (session.isOpen()) {
                try {
                    session.getAsyncRemote().sendPing(pingPayload.asReadOnlyBuffer());
                } catch (IOException e) {
                    logger.warn("Failed to send ping to session {}", session.getId(), e);
                }
            } else {
                unregister(session);
            }
        }
    }

    private void shutdownNow() {
        scheduler.shutdownNow();
        for (Session session : sessions) {
            if (session.isOpen()) {
                try {
                    session.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "Agent shutdown"));
                } catch (IOException e) {
                    logger.warn("Failed to close session {}", session.getId(), e);
                }
            }
        }
    }

    public static void shutdown() {
        if (INSTANCE != null) {
            logger.info("Shutting down");
            INSTANCE.shutdownNow();
        }
    }
}
