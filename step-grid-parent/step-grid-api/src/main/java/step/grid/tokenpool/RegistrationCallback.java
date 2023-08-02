package step.grid.tokenpool;

import java.util.List;

public interface RegistrationCallback<T> {
    /**
     * invoked before registration of an entity
     * @param subject entity to be registered
     * @return true if registration is allowed, false if vetoed
     */
    boolean beforeRegistering(T subject);

    void afterUnregistering(List<T> subject);
}
