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

import step.grid.client.security.ClientSecurityConfiguration;

public class RemoteGridClientImpl extends AbstractGridClientImpl {

	public RemoteGridClientImpl(String gridHost) {
		this(new GridClientConfiguration(), new DefaultTokenLifecycleStrategy(), gridHost, null);
	}

	public RemoteGridClientImpl(String gridHost, ClientSecurityConfiguration gridClientSecurityConfiguration) {
		this(new GridClientConfiguration(), new DefaultTokenLifecycleStrategy(), gridHost, gridClientSecurityConfiguration);
	}
	
	public RemoteGridClientImpl(GridClientConfiguration gridClientConfiguration, String gridHost, ClientSecurityConfiguration gridClientSecurityConfiguration) {
		this(gridClientConfiguration, new DefaultTokenLifecycleStrategy(), gridHost, gridClientSecurityConfiguration);
	}

	public RemoteGridClientImpl(GridClientConfiguration gridClientConfiguration, String gridHost) {
		this(gridClientConfiguration, new DefaultTokenLifecycleStrategy(), gridHost, null);
	}

	public RemoteGridClientImpl(GridClientConfiguration gridClientConfiguration, TokenLifecycleStrategy tokenLifecycleStrategy, String gridHost, ClientSecurityConfiguration gridClientSecurityConfiguration) {
		super(gridClientConfiguration, tokenLifecycleStrategy, new RemoteGridImpl(gridHost, gridClientSecurityConfiguration));
	}
	
}
