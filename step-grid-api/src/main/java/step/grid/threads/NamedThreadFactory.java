package step.grid.threads;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedThreadFactory implements ThreadFactory {
    private final String baseName;
    private final AtomicInteger count = new AtomicInteger(0);
    private static final ConcurrentHashMap<String, AtomicInteger> countByName = new ConcurrentHashMap<>();

    protected NamedThreadFactory(String baseName) {
        this.baseName = baseName;
    }

    public static NamedThreadFactory create(String baseName) {
        return new NamedThreadFactory(baseName);
    }

    @Override
    public Thread newThread(Runnable r) {
        return new Thread(r, baseName + "-" +
                countByName.computeIfAbsent(baseName, n -> new AtomicInteger(0)).getAndIncrement() + "-"
                + count.getAndIncrement());
    }
}
