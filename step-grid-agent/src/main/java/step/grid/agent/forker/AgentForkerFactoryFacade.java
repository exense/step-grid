package step.grid.agent.forker;

import step.grid.contextbuilder.ApplicationContextBuilder;
import step.grid.contextbuilder.LocalResourceApplicationContextFactory;

/**
 * The implementation of the AgentForker depends on multiple libraries that we don't want to have in the class path of the Agent
 * in order to avoid conflicts with
 */
public class AgentForkerFactoryFacade implements AgentForkerFactory {

    private final ApplicationContextBuilder applicationContextBuilder;

    public AgentForkerFactoryFacade(ApplicationContextBuilder applicationContextBuilder) throws Exception {
        this.applicationContextBuilder = applicationContextBuilder;
    }

    @Override
    public AgentForker create(AgentForkerConfiguration configuration, String fileServerHost) throws Exception {
        applicationContextBuilder.resetContext();
        applicationContextBuilder.pushContext(new LocalResourceApplicationContextFactory(this.getClass().getClassLoader(), "step-grid-agent-forker.jar"), false);

        ClassLoader agentForkerClassloader = applicationContextBuilder.getCurrentContext().getClassLoader();
        AgentForker agentForker = applicationContextBuilder.runInContext(() -> {
            AgentForkerFactory factory = (AgentForkerFactory) agentForkerClassloader.loadClass("step.grid.agent.forker.AgentForkerFactoryImpl").getConstructor().newInstance();
            return factory.create(configuration, fileServerHost);
        });

        return agentForker;
    }
}
