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
package step.grid.proxy.services;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.agent.RegistrationMessage;
import step.grid.io.InputMessage;
import step.grid.io.OutputMessage;
import step.grid.proxy.GridProxy;

import java.io.IOException;
import java.net.MalformedURLException;

@Singleton
@Path("/")
public class GridProxyServices {

    private static final Logger logger = LoggerFactory.getLogger(GridProxyServices.class);

    @Inject
    private GridProxy gridProxy;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/grid/register")
    public void register(RegistrationMessage message) throws MalformedURLException {
        gridProxy.handleRegistrationMessage(message);
    }

    @GET
    @Path("/grid/file/{id}/{version}")
    public Response getFile(@PathParam("id") String id, @PathParam("version") String version) throws IOException {
        return gridProxy.forwardGetFileRequest(id, version);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{agentContext}/token/{id}/process")
    public OutputMessage process(@PathParam("agentContext") String agentContext, @PathParam("id") String tokenId, final InputMessage message) {
        return gridProxy.forwardMessageToAgent(agentContext, "process", tokenId, message );
    }

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("{agentContext}/token/{id}/reserve")
    public void reserveToken(@PathParam("agentContext") String agentContext, @PathParam("id") String tokenId)  {
        gridProxy.forwardToAgent(agentContext, "reserve", tokenId);
    }

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("{agentContext}/token/{id}/release")
    public void releaseToken(@PathParam("agentContext") String agentContext, @PathParam("id") String tokenId) {
        gridProxy.forwardToAgent(agentContext, "release", tokenId);
    }

    // For liveness probe
    @GET
    @Path("/running")
    public Response isRunning() {
        if(gridProxy.isRunning()) {
            return Response.status(Response.Status.OK).entity("Grid Proxy is running").build();
        } else {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Grid Proxy is not running").build();
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/shutdown")
    public void shutdown(@Context HttpServletRequest request) {
        logger.info("Received shutdown request from " + request.getRemoteAddr());
        new Thread(() -> {
            try {
                gridProxy.close();
            } catch (Exception e) {
                logger.error("Error while shutting down", e);
            }
        }).start();
    }

}
