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
package step.grid.contextbuilder;

import java.util.concurrent.TimeUnit;

public class ExecutionContextCacheConfiguration {

	private TimeUnit configurationTimeUnit = TimeUnit.MINUTES;

	private long cleanupTimeToLiveMinutes = 1440;

	private boolean enableCleanup = true;

	private long cleanupFrequencyMinutes = 60;

	public ExecutionContextCacheConfiguration() {
	}

	/**
	 * @return the period of time in minutes since the last access of a cached file before it becomes eligible for cleanup. Refer to the cleanup job for details {@link ApplicationContextBuilder#scheduleCleanupJob() }
	 */
	public long getCleanupTimeToLiveMinutes() {
		return cleanupTimeToLiveMinutes;
	}

	public void setCleanupTimeToLiveMinutes(long cleanupTimeToLiveMinutes) {
		this.cleanupTimeToLiveMinutes = cleanupTimeToLiveMinutes;
	}

	/**
	 * @return whether the {@link ApplicationContextBuilder#scheduleCleanupJob() cleanup job} should be enabled
	 */
	public boolean isEnableCleanup() {
		return enableCleanup;
	}

	public void setEnableCleanup(boolean enableCleanup) {
		this.enableCleanup = enableCleanup;
	}

	/**
	 * @return the frequency of cleanup job in minutes. Refer to {@link ApplicationContextBuilder#scheduleCleanupJob() } for more details
	 */
	public long getCleanupFrequencyMinutes() {
		return cleanupFrequencyMinutes;
	}

	public void setCleanupFrequencyMinutes(long cleanupFrequencyMinutes) {
		this.cleanupFrequencyMinutes = cleanupFrequencyMinutes;
	}

	public TimeUnit getConfigurationTimeUnit() {
		return configurationTimeUnit;
	}

	/**
	 * Only used to override the time unit for Junit test
	 * @param configurationTimeUnit the new time unit to be used
	 */
	public void setConfigurationTimeUnit(TimeUnit configurationTimeUnit) {
		this.configurationTimeUnit = configurationTimeUnit;
	}
}
