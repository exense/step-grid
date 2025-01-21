package step.grid.agent.tokenpool;

/**
 * This abstract class is meant to be used for objects following the TokenReservationSession lifecycle when
 * created within a session context, otherwise it is directly closed as any AutoCloseable.<br/>
 * The property inSession must be set explicitly to true when running in a session context. When closing the TokenReservationSession, it will
 * set the flag to false before invoking the close method.
 */
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
