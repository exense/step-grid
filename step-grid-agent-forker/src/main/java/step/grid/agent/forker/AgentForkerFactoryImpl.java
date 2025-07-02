package step.grid.agent.forker;

import java.io.IOException;

public class AgentForkerFactoryImpl implements AgentForkerFactory {

    @Override
    public AgentForkerImpl create(AgentForkerConfiguration configuration, String fileServerHost) throws IOException {
        return new AgentForkerImpl(configuration, fileServerHost);
    }
}
