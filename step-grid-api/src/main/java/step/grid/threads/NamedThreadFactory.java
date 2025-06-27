package step.grid.threads;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedThreadFactory implements ThreadFactory {
    private final String baseName;
    private final AtomicInteger count = new AtomicInteger(0);

    public NamedThreadFactory(String baseName) {
        this.baseName = baseName;
    }

    @Override
    public Thread newThread(Runnable r) {
        return new Thread(r, baseName + "-" + count.getAndIncrement());
    }
}
