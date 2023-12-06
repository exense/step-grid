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
package step.grid.app.configuration;

public class AppConfiguration {

    boolean ssl = false;
    String keyStorePath;
    String keyStorePassword;
    String keyManagerPassword;
    boolean exposeMetrics = false;
    Integer gridReadTimeout = 3000;
    Integer gridConnectTimeout = 3000;

    public AppConfiguration() {
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public String getKeyStorePath() {
        return keyStorePath;
    }

    public void setKeyStorePath(String keyStorePath) {
        this.keyStorePath = keyStorePath;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public String getKeyManagerPassword() {
        return keyManagerPassword;
    }

    public void setKeyManagerPassword(String keyManagerPassword) {
        this.keyManagerPassword = keyManagerPassword;
    }

    public boolean isExposeMetrics() {
        return exposeMetrics;
    }

    public void setExposeMetrics(boolean exposeMetrics) {
        this.exposeMetrics = exposeMetrics;
    }

    public Integer getGridReadTimeout() {
        return gridReadTimeout;
    }

    public void setGridReadTimeout(Integer gridReadTimeout) {
        this.gridReadTimeout = gridReadTimeout;
    }

    public Integer getGridConnectTimeout() {
        return gridConnectTimeout;
    }

    public void setGridConnectTimeout(Integer gridConnectTimeout) {
        this.gridConnectTimeout = gridConnectTimeout;
    }
}
