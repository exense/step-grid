package step.grid.agent.tokenpool;

import java.util.function.Function;

public class UnusableTokenReservationSession extends TokenReservationSession {

    @Override
    public Object get(String arg0) {
        throw unusableSessionException();
    }

    @Override
    public <T> T getOrDefault(String key, T def) {
        throw unusableSessionException();
    }

    @Override
    public <T, U> T getOrDefault(String key, Function<U, T> def, U param) {
        throw unusableSessionException();
    }

    @Override
    public <T> T get(Class<T> objectClass) {
        throw unusableSessionException();
    }

    @Override
    public Object put(String arg0, Object arg1) {
        throw unusableSessionException();
    }

    @Override
    public void put(Object object) {
        throw unusableSessionException();
    }

    private RuntimeException unusableSessionException() {
        // TODO use error codes instead of error messages
        return new RuntimeException("Session object unreachable. Wrap your keywords with a Session node in your test plan in order to make the session object available.");
    }
}
