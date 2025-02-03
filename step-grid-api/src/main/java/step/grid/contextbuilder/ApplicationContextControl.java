package step.grid.contextbuilder;

public class ApplicationContextControl implements AutoCloseable {

    protected final ApplicationContextBuilder.ApplicationContext applicationContext;

    public ApplicationContextControl(ApplicationContextBuilder.ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void close() throws Exception {
        if (applicationContext != null) {
            applicationContext.releaseUsage();
        }
    }
}
