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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.functions.io.AbstractSession;

import java.util.*;

public class TokenReservationSession extends AbstractSession {

    private static final Logger logger = LoggerFactory.getLogger(TokenReservationSession.class);

    private List<TokenEventListener> eventListeners = new ArrayList<>();

    private final LinkedList<AutoCloseable> closeWithSessionSet = new LinkedList<>();

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

    /**
     * Registers the provided AutoCloseable object to be closed at the end of the session's lifecycle.
     * This method also supports fake sessions using the UnusableTokenReservationSession class.
     * It is intended for storing technical objects (such as application context or file versions)
     * that follow the session lifecycle.
     *
     * When keywords are executed outside a session context, the UnusableTokenReservationSession is used
     * and closed from the AgentTokenPool.afterTokenExecution hook.
     *
     * @param closeable the AutoCloseable object to be registered for closure with the session
     */
    public <T extends AutoCloseable> void registerObjectToBeClosedWithSession(T closeable) {
        closeWithSessionSet.add(closeable);
    }

    @Override
    public void close() {
        closeWithSessionSet.descendingIterator().forEachRemaining(autoCloseable -> {
            try {
                autoCloseable.close();
            } catch (Exception e) {
                logger.error("Unable to close {} while closing the session.", autoCloseable, e);
            }
        });
        super.close();
    }
}
