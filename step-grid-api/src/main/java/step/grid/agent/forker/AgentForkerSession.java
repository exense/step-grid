package step.grid.agent.forker;

import step.grid.io.InputMessage;
import step.grid.io.OutputMessage;

public interface AgentForkerSession extends AutoCloseable {
    OutputMessage delegateExecution(InputMessage message) throws Exception;
}
