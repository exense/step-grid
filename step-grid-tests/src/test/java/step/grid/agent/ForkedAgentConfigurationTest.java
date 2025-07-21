package step.grid.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.TokenWrapper;
import step.grid.agent.conf.AgentForkerConfiguration;
import step.grid.client.LocalGridClientImpl;
import step.grid.io.AgentErrorCode;
import step.grid.io.Attachment;
import step.grid.io.AttachmentHelper;
import step.grid.io.OutputMessage;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ForkedAgentConfigurationTest extends AbstractGridTest {

    private static final Logger logger = LoggerFactory.getLogger(ForkedAgentConfigurationTest.class);

    public void init(AgentForkerConfiguration configuration) throws Exception {
        super.init(configuration);
        client = new LocalGridClientImpl(grid);
    }

    @Test
    public void testAgentPortRangeWithContention() throws Exception {
        AgentForkerConfiguration agentForkerConfiguration = enabledAgentForkerConfig();
        // Configure an agent port range with 2 ports
        agentForkerConfiguration.agentPortRangeStart = 8050;
        agentForkerConfiguration.agentPortRangeEnd = 8051;
        init(agentForkerConfiguration);

        CopyOnWriteArrayList<Object> exceptions = new CopyOnWriteArrayList<>();
        // Call the agent with more threads than the configured agent port range size
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        executorService.execute(() -> {
            for(int i = 0; i < 10; i++) {
                try {
                    callToken();
                } catch (Exception e) {
                    logger.error("Error occurred while executing token", e);
                    exceptions.add(e);
                }
            }

        });
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.MINUTES);
        if(!exceptions.isEmpty()) {
            throw new Exception("Multiple exceptions occurred");
        }
    }

    private static AgentForkerConfiguration enabledAgentForkerConfig() {
        AgentForkerConfiguration agentForkerConfiguration = new AgentForkerConfiguration();
        agentForkerConfiguration.enabled = true;
        return agentForkerConfiguration;
    }

    @Test
    public void testStartTimeout() throws Exception {
        AgentForkerConfiguration agentForkerConfiguration = enabledAgentForkerConfig();
        // Configure a very short start timeout
        agentForkerConfiguration.startTimeoutMs = 1;
        init(agentForkerConfiguration);

        TokenWrapper token = client.getTokenHandle(null, Map.of(), true);
        OutputMessage outputMessage = client.call(token.getID(), newDummyJson(), TestTokenHandler.class.getName(), null, null, 5000);
        // An unexpected agent error should be thrown when executing the token
        Assert.assertNotNull(outputMessage.getAgentError());
        Assert.assertEquals(AgentErrorCode.UNEXPECTED, outputMessage.getAgentError().getErrorCode());
        Assert.assertTrue(attachmentAsString(outputMessage.getAttachments().get(0)).contains("Timeout while waiting for the forked agent to start and join the forked agent grid"));
    }

    @Test
    public void testWrongJavaCommand() throws Exception {
        AgentForkerConfiguration agentForkerConfiguration = enabledAgentForkerConfig();
        // Configure a wrong java path
        agentForkerConfiguration.javaPath = "wrong";
        init(agentForkerConfiguration);

        TokenWrapper token = client.getTokenHandle(null, Map.of(), true);
        OutputMessage outputMessage = client.call(token.getID(), newDummyJson(), TestTokenHandler.class.getName(), null, null, 5000);
        // An unexpected agent error should be thrown when executing the token
        Assert.assertNotNull(outputMessage.getAgentError());
        Assert.assertEquals(AgentErrorCode.UNEXPECTED, outputMessage.getAgentError().getErrorCode());
        Assert.assertTrue(attachmentAsString(outputMessage.getAttachments().get(0)).contains("Cannot run program \"wrong\""));
        Assert.assertTrue(attachmentAsString(outputMessage.getAttachments().get(0)).contains("Error while starting forked agent"));
    }

    @Test
    public void testWrongVMargs() throws Exception {
        AgentForkerConfiguration agentForkerConfiguration = enabledAgentForkerConfig();
        // Configure wrong VM args
        agentForkerConfiguration.vmArgs = "-Xmx0G";
        agentForkerConfiguration.startTimeoutMs = 2000;
        init(agentForkerConfiguration);

        TokenWrapper token = client.getTokenHandle(null, Map.of(), true);
        OutputMessage outputMessage = client.call(token.getID(), newDummyJson(), TestTokenHandler.class.getName(), null, null, 5000);
        // An unexpected agent error should be thrown when executing the token
        Assert.assertNotNull(outputMessage.getAgentError());
        Assert.assertEquals(AgentErrorCode.UNEXPECTED, outputMessage.getAgentError().getErrorCode());
        Assert.assertTrue(attachmentAsString(outputMessage.getAttachments().get(0)).contains("Timeout while waiting for the forked agent to start and join the forked agent grid"));
    }

    private static String attachmentAsString(Attachment attachment) {
        return new String(AttachmentHelper.hexStringToByteArray(attachment.getHexContent()));
    }

    private void callToken() throws Exception {
        JsonNode o = newDummyJson();
        TokenWrapper token = client.getTokenHandle(null, Map.of(), true);
        try {
            OutputMessage outputMessage = client.call(token.getID(), o, TestTokenHandler.class.getName(), null, null, 5000);
            Assert.assertEquals(o, outputMessage.getPayload());
        } finally {
            client.returnTokenHandle(token.getID());
        }
    }
}
