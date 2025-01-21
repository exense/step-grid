package step.grid.contextbuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.filemanager.FileManagerException;

import java.io.IOException;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class ApplicationContext implements AutoCloseable {

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
        //TOTO if current usage is 0 should we trigger clean up here. Cleanup would have to notify parent +
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

    /**
     * Method used by descriptor with descriptor.requiresReload() returning true, no implementation exists as of now
     * Legacy implementation was building a new class loader, replacing the current one and clearing the contextObject map
     * With new implementation, it now close first the current classloader and close the contextObject
     * the method aims to recreate the class loader which now required to clean up the previous
     * @param descriptor
     * @param parentContext
     * @throws FileManagerException
     */
    public void reloadContext(ApplicationContextFactory descriptor, ApplicationContext parentContext) throws FileManagerException {
        //Start by cleaning previous context
        cleanup();
        this.descriptor = descriptor;
        ClassLoader classLoader = descriptor.buildClassLoader(parentContext.classLoader);
        if (logger.isDebugEnabled()) {
            logger.debug("Loading classloader for {} in application context builder {}", descriptor.getId(), this);
        }
        this.classLoader = classLoader;
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
            closeContextObjects();
        } else if (logger.isDebugEnabled()) {
            logger.debug("Cannot clean application context {}, children size {}, usage count {} and descriptor instance {}", applicationContextId, childContexts.size(), usage.get(), descriptor);
        }
        return cleanable;
    }

    private void closeContextObjects() {
        contextObjects.forEach((k, v) -> {
            if (v instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) v).close();
                } catch (Exception e) {
                    logger.warn("Unable to close the object with key {}", k);
                }
            }
        });
    }

    @Override
    public void close() throws IOException {
        cleanup();
    }

    private void closeClassLoader() {
        if (classLoader != null && classLoader instanceof AutoCloseable) {
            if (logger.isDebugEnabled()) {
                logger.debug("Application context class loader found for {}, closing classLoader {}", this, classLoader);
                logger.debug("Parent classloader is {}", classLoader.getParent());
                if (classLoader instanceof URLClassLoader) {
                    URLClassLoader classLoader1 = (URLClassLoader) classLoader;
                    logger.debug("URLs: {}", Arrays.asList(classLoader1.getURLs()));
                }
            }
            try {
                ((AutoCloseable) classLoader).close();
            } catch (Exception e) {
                logger.error("Application context class loader found could not be closed for context {}", this, e);
            }
        }
    }
}
