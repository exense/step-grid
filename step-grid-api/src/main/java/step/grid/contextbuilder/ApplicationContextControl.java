package step.grid.contextbuilder;

public class ApplicationContextControl implements AutoCloseable{

    private final ApplicationContext applicationContext;

    public ApplicationContextControl(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void close() throws Exception {
        applicationContext.releaseUsage();
    }
}
