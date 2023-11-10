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

public class FileManagerConfiguration {

	long cleanupLastAccessTimeThresholdMinutes = 1440;

	boolean cleanupJobEnabled = true;

	long cleanupIntervalMinutes = 60;

	public FileManagerConfiguration() {
	}

	public long getCleanupLastAccessTimeThresholdMinutes() {
		return cleanupLastAccessTimeThresholdMinutes;
	}

	public void setCleanupLastAccessTimeThresholdMinutes(long cleanupLastAccessTimeThresholdMinutes) {
		this.cleanupLastAccessTimeThresholdMinutes = cleanupLastAccessTimeThresholdMinutes;
	}

	public boolean isCleanupJobEnabled() {
		return cleanupJobEnabled;
	}

	public void setCleanupJobEnabled(boolean cleanupJobEnabled) {
		this.cleanupJobEnabled = cleanupJobEnabled;
	}

	public long getCleanupIntervalMinutes() {
		return cleanupIntervalMinutes;
	}

	public void setCleanupIntervalMinutes(long cleanupIntervalMinutes) {
		this.cleanupIntervalMinutes = cleanupIntervalMinutes;
	}
}
