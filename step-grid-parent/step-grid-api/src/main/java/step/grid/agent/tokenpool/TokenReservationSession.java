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
package step.grid.agent.tokenpool;

import step.functions.io.AbstractSession;

import java.util.ArrayList;
import java.util.List;

public class TokenReservationSession extends AbstractSession {

    private List<TokenEventListener> eventListeners = new ArrayList<>();

    public boolean registerEventListener(TokenEventListener tokenEventListener) {
        return eventListeners.add(tokenEventListener);
    }

    public boolean removeEventListener(TokenEventListener tokenEventListener) {
        return eventListeners.remove(tokenEventListener);
    }

    public List<TokenEventListener> getEventListeners() {
        return eventListeners;
    }

    public void clearEventListeners() {
        eventListeners.clear();
    }
}
