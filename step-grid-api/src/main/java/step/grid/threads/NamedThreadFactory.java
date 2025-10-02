package step.grid.threads;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedThreadFactory implements ThreadFactory {
    private final boolean daemon;
    private final String baseName;
    private final AtomicInteger count = new AtomicInteger(0);
    private static final ConcurrentHashMap<String, AtomicInteger> countByName = new ConcurrentHashMap<>();

    protected NamedThreadFactory(String baseName, boolean daemon) {
        this.baseName = baseName;
        this.daemon = daemon;
    }


    @Deprecated // prefer the variant with explicit daemon parameter to make behavior clear
    public static NamedThreadFactory create(String baseName) {
        return create(baseName, false);
    }

    public static NamedThreadFactory create(String baseName, boolean daemon) {
        return new NamedThreadFactory(baseName, daemon);
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, baseName + "-" +
                countByName.computeIfAbsent(baseName, n -> new AtomicInteger(0)).getAndIncrement() + "-"
                + count.getAndIncrement());
        t.setDaemon(daemon);
        return t;
    }
}
