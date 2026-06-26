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
     * @param client            the JAX-RS client used to perform the download
     * @param fileServer        the base URL of the upstream file server (without the {@code /grid/file/...} suffix)
     * @param jwtTokenGenerator the JWT token generator used to authenticate the request, or {@code null} if authentication is disabled
     * @param connectionTimeout the connection timeout in milliseconds
     * @param callTimeout       the read timeout in milliseconds
     * @param maxRetries        the maximum number of download retries on transient network errors
     * @param retryDelayMs      the delay between retries in milliseconds
     */
    public HttpFileVersionProvider(Client client, String fileServer, JwtTokenGenerator jwtTokenGenerator,
                                   int connectionTimeout, int callTimeout, int maxRetries, int retryDelayMs) {
        this.client = client;
        this.fileServer = fileServer;
        this.jwtTokenGenerator = jwtTokenGenerator;
        this.connectionTimeout = connectionTimeout;
        this.callTimeout = callTimeout;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
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
        if (response.getStatus() != 200) {
            String error = response.readEntity(String.class);
            throw new RuntimeException("Unexpected server error: " + error);
        } else {
            InputStream in = (InputStream) response.getEntity();
            String contentDisposition = response.getHeaderString("content-disposition");
            if (contentDisposition != null) {
                boolean isDirectory = contentDisposition.contains("type = dir");
                Matcher m = FILENAME_PATTERN.matcher(contentDisposition);
                if (m.find()) {
                    String filename = m.group(1);

                    long t2 = System.currentTimeMillis();
                    File file = new File(container + "/" + filename);
                    if (isDirectory) {
                        FileHelper.unzip(in, file);
                    } else {
                        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))) {
                            FileHelper.copy(in, bos, 1024);
                        }
                    }
                    if (logger.isDebugEnabled()) {
                        logger.debug("Uncompressed file " + fileVersionId + " in " + (System.currentTimeMillis() - t2) + "ms to " + file.getAbsoluteFile());
                    }

                    return new FileVersion(file, fileVersionId, isDirectory);
                } else {
                    throw new RuntimeException("Unable to find filename in header: " + contentDisposition);
                }
            } else {
                throw new RuntimeException("No content-disposition header found in the HTTP response");
            }
        }
    }
}
