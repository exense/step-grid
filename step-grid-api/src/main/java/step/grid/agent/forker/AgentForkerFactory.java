package step.grid.agent.forker;

public interface AgentForkerFactory {
    AgentForker create(AgentForkerConfiguration configuration, String fileServerHost) throws Exception;
}
