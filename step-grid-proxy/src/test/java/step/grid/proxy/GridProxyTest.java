package step.grid.proxy;

import jakarta.ws.rs.client.Client;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import step.grid.AgentRef;
import step.grid.agent.RegistrationMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;

public class GridProxyTest {

    @Test
    public void gridProxyTest() throws Exception {
        //Rest test client will simply throw a "ProxyTestException" to avoid complex mocking
        Client mock = Mockito.mock(Client.class);
        Mockito.when(mock.target(anyString())).thenAnswer( ( InvocationOnMock invocationOnMock) -> {
            throw new ProxyTestException(invocationOnMock.getArgument( 0 ));
        });
        String[] args = {"-config=src/test/resources/GridProxyConf.yaml"};
        GridProxy gridProxy = new GridProxy(args);
        gridProxy.overrideRestClient(mock);
        //Prepare and send registration message
        RegistrationMessage registrationMessage = new RegistrationMessage();
        registrationMessage.setAgentRef(new AgentRef("agentId1","http://agenturl:1234","default"));
        assertThrows("http://localhost:8081/grid/register", ProxyTestException.class, () -> gridProxy.handleRegistrationMessage(registrationMessage));

        //validate URL forwarded to the grid server is the proxyfied one
        String proxifiedAgentUrl = registrationMessage.getAgentRef().getAgentUrl();
        assertTrue(proxifiedAgentUrl.startsWith("http://localhost:8082"));
        Pattern pattern = Pattern.compile("^http://localhost:8082/([^/]+)$");
        Matcher matcher = pattern.matcher(proxifiedAgentUrl);
        assertTrue(matcher.matches());
        String agentContextRoot = matcher.group(1);

        //Simualte call from grid server to proxyfied agent
        assertThrows("http://agenturl:1234/token/sometokenId/reserve", ProxyTestException.class, () -> gridProxy.reserveToken(agentContextRoot, "sometokenId"));
    }

}