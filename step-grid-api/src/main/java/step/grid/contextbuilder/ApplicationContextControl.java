package step.grid.contextbuilder;

import step.grid.agent.tokenpool.SessionAwareCloseable;

public class ApplicationContextControl extends SessionAwareCloseable {

    private final ApplicationContext applicationContext;

    public ApplicationContextControl(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    protected void _close() throws Exception {
        if (applicationContext != null) {
            applicationContext.releaseUsage();
        }
    }
}
