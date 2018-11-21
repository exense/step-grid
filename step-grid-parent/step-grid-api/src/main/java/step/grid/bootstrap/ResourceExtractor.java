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
package step.grid.bootstrap;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public class ResourceExtractor {
	
	public static File extractResource(ClassLoader cl, String resourceName) {
		File gridJar;
		InputStream is = cl.getResourceAsStream(resourceName);
		try {
			gridJar = File.createTempFile(resourceName + "-" + UUID.randomUUID(), resourceName.substring(resourceName.lastIndexOf(".")));
			Files.copy(is, gridJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
			gridJar.deleteOnExit();
			return gridJar; 
		} catch (IOException e) {
			throw new RuntimeException("Error while extracting plugin file", e);
		}
	}

}
