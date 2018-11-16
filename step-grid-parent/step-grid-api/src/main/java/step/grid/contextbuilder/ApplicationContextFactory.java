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
package step.grid.contextbuilder;

import step.grid.filemanager.FileProviderException;

public abstract class ApplicationContextFactory {
	
	public abstract String getId();
	
	public abstract boolean requiresReload() throws FileProviderException;
	
	public abstract ClassLoader buildClassLoader(ClassLoader parentClassLoader) throws FileProviderException;
	
}
