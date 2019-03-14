package step.grid.agent;

import step.grid.agent.handler.MessageHandler;
import step.grid.agent.tokenpool.AgentTokenWrapper;
import step.grid.contextbuilder.ApplicationContextBuilderException;
import step.grid.filemanager.FileManagerException;
import step.grid.filemanager.FileVersionId;
import step.grid.io.InputMessage;
import step.grid.io.OutputMessage;

public class TestMessageHandler implements MessageHandler {

	@Override
	public OutputMessage handle(AgentTokenWrapper token, InputMessage message) throws Exception {
		throw new ApplicationContextBuilderException(new FileManagerException(new FileVersionId("fileId", "1"), null));
	}

}
