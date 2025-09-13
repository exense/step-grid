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
package step.grid.proxy.conf;

import step.grid.app.configuration.AppConfiguration;
import step.grid.security.SymmetricSecurityConfiguration;

public class GridProxyConfiguration extends AppConfiguration {

    private String gridProxyName;
    private String gridProxyHost;
    private Integer gridProxyPort;
    private String gridProxyUrl;

    private String gridUrl;
    private SymmetricSecurityConfiguration gridSecurity;

    private Integer agentConnectTimeout = 3000;
    private Integer agentReserveTimeout = 3000;
    private Integer agentReleaseTimeout = 3000;

    public GridProxyConfiguration() {
        super();
    }

    public String getGridProxyName() {
        return gridProxyName;
    }

    public void setGridProxyName(String gridProxyName) {
        this.gridProxyName = gridProxyName;
    }

    public String getGridProxyHost() {
        return gridProxyHost;
    }

    public void setGridProxyHost(String gridProxyHost) {
        this.gridProxyHost = gridProxyHost;
    }

    public Integer getGridProxyPort() {
        return gridProxyPort;
    }

    public void setGridProxyPort(Integer gridProxyPort) {
        this.gridProxyPort = gridProxyPort;
    }

    public String getGridProxyUrl() {
        return gridProxyUrl;
    }

    public void setGridProxyUrl(String gridProxyUrl) {
        this.gridProxyUrl = gridProxyUrl;
    }

    public String getGridUrl() {
        return gridUrl;
    }

    public void setGridUrl(String gridUrl) {
        this.gridUrl = gridUrl;
    }

    public Integer getAgentConnectTimeout() {
        return agentConnectTimeout;
    }

    public void setAgentConnectTimeout(Integer agentConnectTimeout) {
        this.agentConnectTimeout = agentConnectTimeout;
    }

    public Integer getAgentReserveTimeout() {
        return agentReserveTimeout;
    }

    public void setAgentReserveTimeout(Integer agentReserveTimeout) {
        this.agentReserveTimeout = agentReserveTimeout;
    }

    public Integer getAgentReleaseTimeout() {
        return agentReleaseTimeout;
    }

    public void setAgentReleaseTimeout(Integer agentReleaseTimeout) {
        this.agentReleaseTimeout = agentReleaseTimeout;
    }

    public SymmetricSecurityConfiguration getGridSecurity() {
        return gridSecurity;
    }

    public void setGridSecurity(SymmetricSecurityConfiguration gridSecurity) {
        this.gridSecurity = gridSecurity;
    }
}
