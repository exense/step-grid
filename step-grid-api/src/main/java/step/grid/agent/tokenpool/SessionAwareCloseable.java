package step.grid.agent.tokenpool;

public abstract class SessionAwareCloseable implements AutoCloseable {

    private boolean inSession = false;

    public void setInSession(boolean inSession) {
        this.inSession = inSession;
    }

    @Override
    public void close() throws Exception {
        if (!inSession) {
            _close();
        }
    }

    protected abstract void _close() throws Exception;
}
