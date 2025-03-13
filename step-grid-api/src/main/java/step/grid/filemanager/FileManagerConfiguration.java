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
package step.grid.filemanager;

import java.util.concurrent.TimeUnit;

public class FileManagerConfiguration {

	private TimeUnit configurationTimeUnit = TimeUnit.MINUTES;

	private long cleanupTimeToLiveMinutes = 1440;

	private boolean enableCleanup = true;

	private long cleanupFrequencyMinutes = 60;

	public FileManagerConfiguration() {
	}

	/**
	 * @return the period of time in minutes since the last access of a cached file before it becomes eligible for cleanup. Refer to the cleanup job for details {@link AbstractFileManager#scheduleCleanupJob() }
	 */
	public long getCleanupTimeToLiveMinutes() {
		return cleanupTimeToLiveMinutes;
	}

	public void setCleanupTimeToLiveMinutes(long cleanupTimeToLiveMinutes) {
		this.cleanupTimeToLiveMinutes = cleanupTimeToLiveMinutes;
	}

	/**
	 * @return whether the {@link AbstractFileManager#scheduleCleanupJob() cleanup job} should be enabled
	 */
	public boolean isEnableCleanup() {
		return enableCleanup;
	}

	public void setEnableCleanup(boolean enableCleanup) {
		this.enableCleanup = enableCleanup;
	}

	/**
	 * @return the frequency of cleanup job in minutes. Refer to {@link AbstractFileManager#scheduleCleanupJob() } for more details
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
	 * Change the time unit used for the cleanup scheduled job for junit tests, default time unit is Minutes
	 * @param configurationTimeUnit
	 */
	protected void setConfigurationTimeUnit(TimeUnit configurationTimeUnit) {
		this.configurationTimeUnit = configurationTimeUnit;
	}
}
