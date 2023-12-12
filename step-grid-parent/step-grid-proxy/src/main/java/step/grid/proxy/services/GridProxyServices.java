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
import step.grid.io.AbstractGridServices;
import step.grid.io.InputMessage;
import step.grid.io.OutputMessage;
import step.grid.proxy.GridProxy;

@Singleton
@Path("/")
public class GridProxyServices extends AbstractGridServices {

    private static final Logger logger = LoggerFactory.getLogger(GridProxyServices.class);

    @Inject
    private GridProxy gridProxy;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/grid/register")
    public void register(RegistrationMessage message) throws GridProxyException {
        try {
            gridProxy.handleRegistrationMessage(message);
        } catch (Exception e) {
            if (message != null && message.getAgentRef() != null) {
                throw new GridProxyException("Registration failed for agent ref '" + message.getAgentRef() + "'", e);
            } else {
                throw new GridProxyException("Registration failed due to invalid payload", e);
            }

        }
    }

    @GET
    @Path("/grid/file/{id}/{version}")
    public Response getFile(@PathParam("id") String id, @PathParam("version") String version) throws GridProxyException {
        try {
            return gridProxy.forwardGetFileRequest(id, version);
        } catch (Exception e) {
            throw new GridProxyException("Unable to get file with id '" + id + "' and version '" + version + "'", e);
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{agentContext}/token/{id}/process")
    public OutputMessage process(@PathParam("agentContext") String agentContext, @PathParam("id") String tokenId, final InputMessage message) throws GridProxyException {
        try {
            return gridProxy.forwardMessageToAgent(agentContext, "process", tokenId, message );
        } catch (Exception e) {
            return handleUnexpectedError(e);
        }
    }

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("{agentContext}/token/{id}/reserve")
    public void reserveToken(@PathParam("agentContext") String agentContext, @PathParam("id") String tokenId) throws GridProxyException {
        try {
            gridProxy.reserveToken(agentContext, tokenId);
        } catch (Exception e) {
            throw new GridProxyException("Unable to reserve token for agent with context '" + agentContext + "'", e);
        }
    }

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("{agentContext}/token/{id}/release")
    public void releaseToken(@PathParam("agentContext") String agentContext, @PathParam("id") String tokenId) throws GridProxyException {
        try {
            gridProxy.releaseToken(agentContext, tokenId);
        } catch (Exception e) {
            throw new GridProxyException("Unable to release token for agent with context '" + agentContext + "'", e);
        }
    }

    // For liveness probe
    @GET
    @Path("/ready")
    public Response isRunning() {
        if(gridProxy.isRunning()) {
            return Response.status(Response.Status.OK).entity("Grid Proxy is running").build();
        } else {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Grid Proxy is not running").build();
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/stop")
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
