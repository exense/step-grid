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

import step.grid.filemanager.FileManagerConfiguration;

import java.util.concurrent.TimeUnit;

public class ApplicationContextConfiguration {

	private TimeUnit configurationTimeUnit = TimeUnit.MINUTES;

	private long cleanupLastAccessTimeThresholdMinutes = 120;

	private boolean cleanupEnabled = true;

	private long cleanupIntervalMinutes = 60;

	public ApplicationContextConfiguration() {
	}

	/**
	 * Constructor used to align the cleanup configuration with the file manager configuration
	 * Should be used whenever the configuration for the application context is not explicitly provided
	 * @param fileManagerConfiguration the file manager configuration to extract the configuration from
	 */
	public ApplicationContextConfiguration(FileManagerConfiguration fileManagerConfiguration) {
		this.cleanupEnabled = fileManagerConfiguration.isCleanupEnabled();
		this.cleanupIntervalMinutes = fileManagerConfiguration.getCleanupIntervalMinutes();
		this.cleanupLastAccessTimeThresholdMinutes = fileManagerConfiguration.getCleanupLastAccessTimeThresholdMinutes();
	}

	/**
	 * @return the period of time in minutes since the last access of a cached file before it becomes eligible for cleanup. Refer to the cleanup job for details {@link ApplicationContextBuilder#scheduleCleanupJob() }
	 */
	public long getCleanupLastAccessTimeThresholdMinutes() {
		return cleanupLastAccessTimeThresholdMinutes;
	}

	public void setCleanupLastAccessTimeThresholdMinutes(long cleanupLastAccessTimeThresholdMinutes) {
		this.cleanupLastAccessTimeThresholdMinutes = cleanupLastAccessTimeThresholdMinutes;
	}

	/**
	 * @return whether the {@link ApplicationContextBuilder#scheduleCleanupJob() cleanup job} should be enabled
	 */
	public boolean isCleanupEnabled() {
		return cleanupEnabled;
	}

	public void setCleanupEnabled(boolean cleanupEnabled) {
		this.cleanupEnabled = cleanupEnabled;
	}

	/**
	 * @return the frequency of cleanup job in minutes. Refer to {@link ApplicationContextBuilder#scheduleCleanupJob() } for more details
	 */
	public long getCleanupIntervalMinutes() {
		return cleanupIntervalMinutes;
	}

	public void setCleanupIntervalMinutes(long cleanupIntervalMinutes) {
		this.cleanupIntervalMinutes = cleanupIntervalMinutes;
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
