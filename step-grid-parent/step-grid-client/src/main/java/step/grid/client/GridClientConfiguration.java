/*******************************************************************************
 * (C) Copyright 2018 Jerome Comte and Dorian Cransac
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
 *******************************************************************************/
package step.grid.client;

public class GridClientConfiguration {

	private long noMatchExistsTimeout = 10000;
	
	private long matchExistsTimeout = 60000;
	
	private int releaseSessionTimeout = 60000;
	
	private int reserveSessionTimeout = 10000;
	
	private int readTimeoutOffset = 3000;

	public long getNoMatchExistsTimeout() {
		return noMatchExistsTimeout;
	}

	public void setNoMatchExistsTimeout(long noMatchExistsTimeout) {
		this.noMatchExistsTimeout = noMatchExistsTimeout;
	}

	public long getMatchExistsTimeout() {
		return matchExistsTimeout;
	}

	public void setMatchExistsTimeout(long matchExistsTimeout) {
		this.matchExistsTimeout = matchExistsTimeout;
	}

	public int getReleaseSessionTimeout() {
		return releaseSessionTimeout;
	}

	public void setReleaseSessionTimeout(int releaseSessionTimeout) {
		this.releaseSessionTimeout = releaseSessionTimeout;
	}

	public int getReserveSessionTimeout() {
		return reserveSessionTimeout;
	}

	public void setReserveSessionTimeout(int reserveSessionTimeout) {
		this.reserveSessionTimeout = reserveSessionTimeout;
	}

	public int getReadTimeoutOffset() {
		return readTimeoutOffset;
	}

	public void setReadTimeoutOffset(int readTimeoutOffset) {
		this.readTimeoutOffset = readTimeoutOffset;
	}
}
