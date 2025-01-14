package step.grid.contextbuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.filemanager.FileManagerException;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class ApplicationContext implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationContext.class);

    private final AtomicInteger usage = new AtomicInteger(0);

    private final String applicationContextId;

    private ClassLoader classLoader;

    private ApplicationContextFactory descriptor;

    private Map<String, ApplicationContext> childContexts = new ConcurrentHashMap<>();

    private ConcurrentHashMap<String, Object> contextObjects = new ConcurrentHashMap<>();

    protected ApplicationContext(ApplicationContextFactory descriptor, ApplicationContext parentContext, String applicationContextId) throws FileManagerException {
        super();
        this.descriptor = descriptor;
        this.applicationContextId = applicationContextId;
        buildClassLoader(parentContext);
    }

    protected ApplicationContext(ClassLoader classLoader, String applicationContextId) {
        super();
        this.classLoader = classLoader;
        this.applicationContextId = applicationContextId;
        this.descriptor = null;
    }

    public Object get(Object key) {
        return contextObjects.get(key);
    }

    public Object computeIfAbsent(String key, Function<? super String, Object> mappingFunction) {
        return contextObjects.computeIfAbsent(key, mappingFunction);
    }

    public Object put(String key, Object value) {
        return contextObjects.put(key, value);
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void registerUsage() {
        usage.incrementAndGet();
    }

    public void releaseUsage() {
        usage.decrementAndGet();
    }

    public ApplicationContext getChildContext(String applicationContextId) {
        return childContexts.get(applicationContextId);
    }

    public boolean containsChildContextKey(String applicationContextId) {
        return childContexts.containsKey(applicationContextId);
    }

    public void putChildContext(String applicationContextId, ApplicationContext applicationContext) {
        childContexts.put(applicationContextId, applicationContext);
    }

    private void buildClassLoader(ApplicationContext parentContext) throws FileManagerException {
        ClassLoader classLoader = descriptor.buildClassLoader(parentContext.classLoader);
        if (logger.isDebugEnabled()) {
            logger.debug("Loading classloader for {} in application context builder {}", descriptor.getId(), this);
        }
        this.classLoader = classLoader;
        if (logger.isDebugEnabled()) {
            logger.debug("Loaded classloader {}", classLoader);
        }
    }

    public void reloadContext(ApplicationContextFactory descriptor, ApplicationContext parentContext) throws FileManagerException {
        //Start by cleaning previous context
        closeClassLoader();
        //TODO keeping previous logic, but we may have to do more cleanup before updating the descriptor and reloading the classloader
        this.descriptor = descriptor;
        ClassLoader classLoader = descriptor.buildClassLoader(parentContext.classLoader);
        if (logger.isDebugEnabled()) {
            logger.debug("Loading classloader for {} in application context builder {}", descriptor.getId(), this);
        }
        this.classLoader = classLoader;
        //TODO is clean up contextObjects required (i..e close any autoCloseable
        contextObjects.clear();
    }

    public boolean cleanup() {
        //Clean up recursively all children, remove the cleaned up one from the map
        childContexts.entrySet().removeIf(childAppContextEntry -> {
            if (logger.isDebugEnabled()) {
                logger.debug("Cleaning application child context {}", childAppContextEntry.getKey());
            }
            return childAppContextEntry.getValue().cleanup();
        });
        // if all children were cleaned up and usage of current is 0, clean it up too
        boolean cleanable = usage.get() == 0 && childContexts.isEmpty() && descriptor != null;
        if (cleanable) {
            if (logger.isDebugEnabled()) {
                logger.debug("Cleaning up classloader {} for app context {}", classLoader, applicationContextId);
            }
            closeClassLoader();
            if (logger.isDebugEnabled()) {
                logger.debug("Notifying application context factory");
            }
            descriptor.onClassLoaderClosed();
            if (logger.isDebugEnabled()) {
                logger.debug("Closing application context object map");
            }
            contextObjects.forEach((k, v) -> {
                if (v instanceof AutoCloseable) {
                    try {
                        ((AutoCloseable) v).close();
                    } catch (Exception e) {
                        logger.warn("Unable to close the object with key {}", k);
                    }
                }
            });
        } else if (logger.isDebugEnabled()) {
            logger.debug("Cannot clean application context {}, children size {}, usage count {} and descriptor instance {}", applicationContextId, childContexts.size(), usage.get(), descriptor);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Closing application context object map");
        }

        return cleanable;
    }

    @Override
    public void close() throws IOException {
        cleanup();
    }

    private void closeClassLoader() {
        if (classLoader != null && classLoader instanceof AutoCloseable) {
            if (logger.isDebugEnabled()) {
                logger.debug("Application context class loader found for {}, closing classLoader {}", this, classLoader);
            }
            try {
                ((AutoCloseable) classLoader).close();
            } catch (Exception e) {
                logger.error("Application context class loader found could not be closed for context {}", this, e);
            }
        }
    }
}
