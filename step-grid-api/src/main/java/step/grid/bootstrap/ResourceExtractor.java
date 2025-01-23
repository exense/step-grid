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
package step.grid.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public class ResourceExtractor {

	private static final Logger logger = LoggerFactory.getLogger(ResourceExtractor.class);
	/**
	 * Extract a JAR from classloader to a temporary folder with delete on exit. This method is synchronized:
	 * When called from multiple threads with a shared classloader, the input stream can be closed by another thread and cause "Stream closed" exceptions
	 * @param cl the classsloader to extract the resource from
	 * @param resourceName the name of the resource to be extracted
	 * @return the extracted file
	 */
	public static synchronized File extractResource(ClassLoader cl, String resourceName) {
		if (logger.isDebugEnabled()) {
			logger.debug("Extracting resource {} from classloader {}", resourceName, cl);
		}
		File gridJar;
		try (InputStream is = cl.getResourceAsStream(resourceName)) {
			gridJar = File.createTempFile(resourceName + "-" + UUID.randomUUID(), resourceName.substring(resourceName.lastIndexOf(".")));
			Files.copy(is, gridJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
			gridJar.deleteOnExit();
			if (logger.isDebugEnabled()) {
				logger.debug("Extracted resource {} from classloader {} to file {}", resourceName, cl, gridJar.getAbsolutePath());
			}
			return gridJar;
		} catch (IOException e) {
			logger.error("Exception while extracting resource", e);
			throw new RuntimeException("Error while extracting plugin file", e);
		}
	}

}
