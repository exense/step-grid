/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
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
