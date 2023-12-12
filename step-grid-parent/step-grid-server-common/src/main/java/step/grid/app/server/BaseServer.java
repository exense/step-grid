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
package step.grid.app.server;

import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.servlet.jakarta.exporter.MetricsServlet;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.app.configuration.AppConfiguration;

import java.net.Inet4Address;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

public class BaseServer {

    private static final Logger logger = LoggerFactory.getLogger(BaseServer.class);
    protected Server startServer(AppConfiguration appConfiguration, int port, ResourceConfig resourceConfig) throws Exception {
        resourceConfig.register(JacksonJsonProvider.class);
        resourceConfig.register(JacksonFeature.class);

        ServletContainer servletContainer = new ServletContainer(resourceConfig);
        ServletHolder sh = new ServletHolder(servletContainer);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.addServlet(sh, "/*");

        Server server = new Server();

        ServerConnector connector;
        if (appConfiguration.isSsl()) {
            String keyStorePath = appConfiguration.getKeyStorePath();
            String keyStorePassword = appConfiguration.getKeyStorePassword();
            String keyManagerPassword = appConfiguration.getKeyManagerPassword();

            HttpConfiguration https = new HttpConfiguration();
            SecureRequestCustomizer secureRequestCustomizer = new SecureRequestCustomizer();
            //require to accept local host connection
            secureRequestCustomizer.setSniHostCheck(false);
            https.addCustomizer(secureRequestCustomizer);

            SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
            sslContextFactory.setKeyStorePath(keyStorePath);
            sslContextFactory.setKeyStorePassword(keyStorePassword);
            sslContextFactory.setKeyManagerPassword(keyManagerPassword);

            connector = new ServerConnector(server, new SslConnectionFactory(sslContextFactory, "http/1.1"),
                    new HttpConnectionFactory(https));
        } else {
            HttpConfiguration http = new HttpConfiguration();
            http.addCustomizer(new SecureRequestCustomizer());

            connector = new ServerConnector(server);
            connector.addConnectionFactory(new HttpConnectionFactory(http));
        }
        connector.setPort(port);
        server.addConnector(connector);

        ContextHandlerCollection contexts = new ContextHandlerCollection();
        contexts.addHandler(context);
        addMetricServletIfRequired(appConfiguration, contexts);
        server.setHandler(contexts);

        server.start();

        return server;
    }

    private void addMetricServletIfRequired(AppConfiguration appConfiguration, ContextHandlerCollection handlers) {
        if (appConfiguration.isExposeMetrics()) {
            ServletContextHandler servletContext = new ServletContextHandler();
            servletContext.setContextPath("/metrics");
            servletContext.addServlet(new ServletHolder(new MetricsServlet()), "");
            //Start default JVM metrics
            DefaultExports.initialize();
            handlers.addHandler(servletContext);

            logger.info("Exposing prometheus JVM metrics under path '/metrics'");
        }
    }

    protected int resolveServerPort(String configUrl, Integer configPort) throws MalformedURLException {
        int port;
        if (configPort != null) {
            port = configPort;
        } else {
            if (configUrl != null) {
                URL url = new URL(configUrl);
                int urlPort = url.getPort();
                port = urlPort != -1 ? urlPort : url.getDefaultPort();
            } else {
                port = 0;
            }
        }
        return port;
    }

    protected int getActualServerPort(Server server) {
        return ((ServerConnector) server.getConnectors()[0]).getLocalPort();
    }

    protected String getOrBuildActualUrl(String serverHost, String serverUrl, int localPort, boolean ssl)
            throws UnknownHostException {
        String actualAgentUrl;
        if (serverUrl == null) {
            // agentUrl not set. generate it
            String scheme;
            if (ssl) {
                scheme = "https://";
            } else {
                scheme = "http://";
            }

            String host;
            if (serverHost == null) {
                // agentHost not specified. Calculate it
                host = Inet4Address.getLocalHost().getCanonicalHostName();
            } else {
                host = serverHost;
            }
            actualAgentUrl = scheme + host + ":" + localPort;
        } else {
            actualAgentUrl = serverUrl;
        }
        return actualAgentUrl;
    }
}
