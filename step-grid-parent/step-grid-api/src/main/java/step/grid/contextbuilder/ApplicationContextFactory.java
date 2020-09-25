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

import step.grid.filemanager.FileManagerException;

public abstract class ApplicationContextFactory {
	
	/**
	 * @return a unique ID for this factory. This ID is used by the {@link ApplicationContextBuilder} as context key.
	 * The context key uniquely identifies a context instance in the context map. The choice of the Key directly
	 * determines if a context already exists or has to be created (again).
	 */
	public abstract String getId();
	
	/**
	 * Determines if a specific context has to be reloaded
	 * @return true of false depending if a context has to be reloaded or not
	 * @throws FileManagerException
	 */
	public abstract boolean requiresReload() throws FileManagerException;
	
	/**
	 * Builds the {@link ClassLoader} of the context identified by the ID generated by getId()
	 * @param parentClassLoader the parent classloader to be used
	 * @return the {@link ClassLoader} instance built
	 * @throws FileManagerException
	 */
	public abstract ClassLoader buildClassLoader(ClassLoader parentClassLoader) throws FileManagerException;
	
}
