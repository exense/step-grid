package step.grid.agent;

import org.junit.Before;
import org.junit.Test;
import step.grid.TokenWrapper;
import step.grid.TokenWrapperState;
import step.grid.agent.conf.AgentConf;
import step.grid.client.GridClientConfiguration;
import step.grid.client.LocalGridClientImpl;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.CompletableFuture.delayedExecutor;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.junit.Assert.assertEquals;

public class AgentStopTest extends AbstractGridTest {

    @Before
    public void init() throws Exception {
        super.init();

        GridClientConfiguration gridClientConfiguration = new GridClientConfiguration();
        gridClientConfiguration.setNoMatchExistsTimeout(2000);
        client = new LocalGridClientImpl(gridClientConfiguration, grid);
    }

    @Override
    protected void configureAgent(AgentConf agentConf) {
        agentConf.setGracefulShutdownTimeout(10_000L);
    }

    @Test
    public void testAgentShutdownWithOneToken() throws Exception {
        // Select a token and call it
        TokenWrapper token = selectToken();
        callToken(token);

        CompletableFuture<Boolean> future = supplyAsync(() -> {
            // After 1s the state of the token should have been switched to MAINTENANCE_REQUESTED as it is still reserved
            grid.getTokens().forEach(t -> assertEquals(TokenWrapperState.MAINTENANCE_REQUESTED, t.getState()));
            // We return the token to the grid
            returnToken(token);
            return true;
        }, delayedExecutor(1, TimeUnit.SECONDS));

        // We immediately call the close() hook of the agent the shut it down
        agent.close();

        // With a TTL of 60s per default, all tokens should still be in the GRID at that point
        // but their states should have been switched to MAINTENANCE
        grid.getTokens().forEach(t -> assertEquals(TokenWrapperState.MAINTENANCE, t.getState()));

        // Ensure no exception occurred in the async task
        future.get();
    }

    @Test
    public void testAgentShutdownWithMultipleTokens() throws Exception {
        // Add 10 tokens to the grid
        addToken(10, Map.of("att1", "val1"));

        // Select all tokens to wait for them to appear in the grid
        ArrayList<TokenWrapper> tokens = new ArrayList<>();
        for(int i = 0; i<10; i++) {
            tokens.add(selectToken());
        }

        // Return the tokens
        tokens.forEach(this::returnToken);

        CompletableFuture<Boolean> future = supplyAsync(() -> {
            // After 1s the state of the token should have been switched to MAINTENANCE as the token have been released
            grid.getTokens().forEach(t -> assertEquals(TokenWrapperState.MAINTENANCE, t.getState()));
            return true;
        }, delayedExecutor(1, TimeUnit.SECONDS));

        // We immediately call the close() hook of the agent the shut it down
        agent.close();

        // With a TTL of 60s per default, all tokens should still be in the GRID at that point
        // but their states should have been switched to MAINTENANCE
        grid.getTokens().forEach(t -> assertEquals(TokenWrapperState.MAINTENANCE, t.getState()));

        // Ensure no exception occurred in the async task
        future.get();
    }

    @Override
    protected void returnToken(TokenWrapper token) {
        try {
            super.returnToken(token);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
