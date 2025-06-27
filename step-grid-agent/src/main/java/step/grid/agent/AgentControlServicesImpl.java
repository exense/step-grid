package step.grid.agent;

import java.util.concurrent.CompletableFuture;

public class AgentControlServicesImpl implements AgentControlServices {

    private final Agent agent;

    public AgentControlServicesImpl(Agent agent) {
        this.agent = agent;
    }

    public void shutdownAgent() {
        CompletableFuture.runAsync(() -> {
            try {
                agent.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
