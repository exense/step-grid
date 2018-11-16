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
package step.commons.helpers;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

public class FileHelperTest {

	@Test
	public void test() throws IOException {
		long t1 = System.currentTimeMillis();
		byte[] bytes = FileHelper.zipDirectory(new File("C:/Users/jcomte/git/step-enterprise/pdftest-plugin/pdftest-plugin-handler/src/test/resources/test_zip"));
		System.out.println("Compress: "+(System.currentTimeMillis()-t1));
		
		System.out.println("Size "+bytes.length);
		
		long t2 = System.currentTimeMillis();
		FileHelper.extractFolder(bytes, new File("C:/Users/jcomte/git/step-enterprise/pdftest-plugin/pdftest-plugin-handler/src/test/resources/test_zip_out"));
		System.out.println("Decompress: "+(System.currentTimeMillis()-t2));
	}

}
