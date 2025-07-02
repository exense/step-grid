package step.grid.agent.forker;

public interface AgentForker extends AutoCloseable {
    AgentForkerSession newSession() throws Exception;

    boolean isEnabled();
}
