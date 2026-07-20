/*******************************************************************************
 * Copyright (C) 2020, exense GmbH
 *
 * This file is part of STEP
 *
 * STEP is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * STEP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with STEP.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package step.grid.client;

import ch.exense.commons.io.FileHelper;
import ch.exense.commons.resilience.RetryHelper;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.client.security.JwtTokenGenerator;
import step.grid.filemanager.ControllerCallException;
import step.grid.filemanager.ControllerCallTimeout;
import step.grid.filemanager.FileManagerException;
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;
import step.grid.filemanager.FileVersionProvider;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reusable {@link FileVersionProvider} which downloads a {@link FileVersion} over HTTP from an upstream
 * file server exposing the controller-compatible {@code GET /grid/file/{id}/{version}} endpoint.
 * <p>
 * It is shared by the components that need to fetch artifacts from an upstream: the agent (downloading from
 * the controller/grid), the grid proxy (downloading from the grid) and the forked agent (downloading from
 * its main agent). The same download + unzip semantics and the same retry policy apply in all cases.
 */
public class HttpFileVersionProvider implements FileVersionProvider {

    private static final Logger logger = LoggerFactory.getLogger(HttpFileVersionProvider.class);

    public static final List<Class<? extends Exception>> RETRY_FOR_EXCEPTIONS = Stream.concat(
        RetryHelper.COMMON_NETWORK_EXCEPTIONS.stream(),
        Stream.of(ControllerCallException.class, ControllerCallTimeout.class)
    ).collect(Collectors.toList());

    private static final Pattern FILENAME_PATTERN = Pattern.compile(".*filename = (.+?);.*");

    private final Client client;
    private final String fileServer;
    private final JwtTokenGenerator jwtTokenGenerator;
    private final int connectionTimeout;
    private final int callTimeout;
    private final int maxRetries;
    private final int retryDelayMs;
    /**
     * Whether directory artifacts are exploded (unzipped) on download. Executing consumers (a regular agent or a
     * forked agent) need the exploded tree to build classpaths; serve-only caches (the grid proxy and a main
     * agent running in forker mode) keep the archive as-is and re-serve it verbatim, avoiding a pointless
     * unzip-on-download + zip-on-serve round-trip.
     */
    private final boolean explodeDirectories;

    /**
     * @param client            the JAX-RS client used to perform the download
     * @param fileServer        the base URL of the upstream file server (without the {@code /grid/file/...} suffix)
     * @param jwtTokenGenerator the JWT token generator used to authenticate the request, or {@code null} if authentication is disabled
     * @param connectionTimeout the connection timeout in milliseconds
     * @param callTimeout       the read timeout in milliseconds
     * @param maxRetries        the maximum number of download retries on transient network errors
     * @param retryDelayMs      the delay between retries in milliseconds
     * @param explodeDirectories whether directory artifacts are unzipped on download ({@code true}, for executing
     *                           consumers) or kept archived and stored as-is ({@code false}, for serve-only caches)
     */
    public HttpFileVersionProvider(Client client, String fileServer, JwtTokenGenerator jwtTokenGenerator,
                                   int connectionTimeout, int callTimeout, int maxRetries, int retryDelayMs, boolean explodeDirectories) {
        this.client = client;
        this.fileServer = fileServer;
        this.jwtTokenGenerator = jwtTokenGenerator;
        this.connectionTimeout = connectionTimeout;
        this.callTimeout = callTimeout;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
        this.explodeDirectories = explodeDirectories;
    }

    private Invocation.Builder withAuthentication(Invocation.Builder requestBuilder) {
        return JwtTokenGenerator.withAuthentication(jwtTokenGenerator, requestBuilder);
    }

    @Override
    public FileVersion saveFileVersionTo(FileVersionId fileVersionId, File container) throws FileManagerException {
        try {
            return RetryHelper.executeWithRetryOnExceptions(
                () -> downloadAndSaveFileVersion(fileVersionId, container),
                maxRetries,
                retryDelayMs,
                RETRY_FOR_EXCEPTIONS,
                "Download file " + fileVersionId
            );
        } catch (Exception e) {
            throw new FileManagerException(fileVersionId, e);
        }
    }

    private FileVersion downloadAndSaveFileVersion(FileVersionId fileVersionId, File container) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("Downloading file version " + fileVersionId + " from upstream file server " + fileServer);
        }
        Response response;
        try {
            response = withAuthentication(client.target(fileServer + "/grid/file/" + fileVersionId.getFileId() + "/" + fileVersionId.getVersion()).request())
                .property(ClientProperties.READ_TIMEOUT, callTimeout)
                .property(ClientProperties.CONNECT_TIMEOUT, connectionTimeout).get();
        } catch (ProcessingException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException) {
                String causeMessage = cause.getMessage();
                if (causeMessage != null && causeMessage.contains("Read timed out")) {
                    throw new ControllerCallTimeout(e, callTimeout);
                } else {
                    throw new ControllerCallException(e);
                }
            } else {
                throw new ControllerCallException(e);
            }
        }
        // Always close the Response (and its underlying entity stream) regardless of how header/entity
        // processing terminates, to avoid leaking the connection and the file descriptor.
        try {
            if (response.getStatus() != 200) {
                String error = response.readEntity(String.class);
                throw new RuntimeException("Unexpected server error: " + error);
            }
            // Acquire the entity stream up-front in a try-with-resources so it is always closed, even if the
            // header validation below throws. Relying on response.close() alone is not always sufficient to
            // release an unconsumed entity stream (and the connection it holds) with some JAX-RS connectors.
            try (InputStream in = (InputStream) response.getEntity()) {
                String contentDisposition = response.getHeaderString("content-disposition");
                if (contentDisposition == null) {
                    throw new RuntimeException("No content-disposition header found in the HTTP response");
                }
                boolean isDirectory = contentDisposition.contains("type = dir");
                Matcher m = FILENAME_PATTERN.matcher(contentDisposition);
                if (!m.find()) {
                    throw new RuntimeException("Unable to find filename in header: " + contentDisposition);
                }
                String filename = m.group(1);

                long t2 = System.currentTimeMillis();
                File file = resolveWithinContainer(container, filename);
                if (isDirectory && explodeDirectories) {
                    FileHelper.unzip(in, file);
                } else {
                    // A plain file, or a directory kept archived for a serve-only cache: store the stream as-is.
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))) {
                        FileHelper.copy(in, bos, 1024);
                    }
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Stored file " + fileVersionId + " in " + (System.currentTimeMillis() - t2) + "ms to " + file.getAbsoluteFile());
                }

                return new FileVersion(file, fileVersionId, isDirectory);
            }
        } finally {
            response.close();
        }
    }

    /**
     * Resolves the destination file for the given upstream-supplied filename, guarding against path
     * traversal. The {@code filename} comes from the upstream {@code content-disposition} header and must
     * not be trusted: a compromised or malicious upstream could return a value containing traversal
     * sequences (e.g. {@code ../../evil}) to write outside the target container. We therefore reject any
     * resolved path whose canonical form escapes the container.
     */
    private static File resolveWithinContainer(File container, String filename) throws IOException {
        File file = new File(container, filename);
        String containerCanonical = container.getCanonicalPath();
        String fileCanonical = file.getCanonicalPath();
        if (!fileCanonical.equals(containerCanonical)
            && !fileCanonical.startsWith(containerCanonical + File.separator)) {
            throw new IOException("Refusing to write file outside of the target container. Container: "
                + containerCanonical + ", resolved file: " + fileCanonical + ", filename from header: " + filename);
        }
        return file;
    }
}
