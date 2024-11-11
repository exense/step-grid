package step.grid.client;
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
import com.fasterxml.jackson.core.StreamReadConstraints;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;

import com.fasterxml.jackson.databind.ObjectMapper;

@Provider
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GridObjectMapperResolver implements ContextResolver<ObjectMapper> {

    private final ObjectMapper mapper;

    //HK2 still require a public no arg constructor, also used when no custom configuration is required (ex RemoteGridImpl)
    public GridObjectMapperResolver() {
        mapper = new ObjectMapper();
    }

    public GridObjectMapperResolver(int maxStringLength) {
        this();
        mapper.getFactory()
                .setStreamReadConstraints(StreamReadConstraints.builder().maxStringLength(maxStringLength).build());
    }

    @Override
    public ObjectMapper getContext(Class<?> type) {
        return mapper;
    }
}
