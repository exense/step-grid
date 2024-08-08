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
package step.grid.app.configuration;

import ch.exense.commons.app.ArgumentParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigurationParser<T extends AppConfiguration> {
	
	private static final String JSON = "json";
	private static final String YAML = "yaml";

	public T parse(ArgumentParser arguments, File file, Class<T> clazz) throws Exception {
		byte[] bytes = Files.readAllBytes(file.toPath());
		
		String content = new String(bytes);
		
		String resolvedContent = replacePlaceholders(arguments, content);
	
		ObjectMapper mapper;
		if(file.getName().endsWith(YAML)) {
			mapper = new ObjectMapper(new YAMLFactory());
		} else if(file.getName().endsWith(JSON)) {
			mapper = new ObjectMapper();
		} else {
			throw new IllegalArgumentException("Unsupported file type for agent configuration: "
					+ file.getAbsolutePath() + ". Supported file types are .yaml and .json");
		}
		
		return mapper.readValue(resolvedContent, clazz);
	}
	
	private String replacePlaceholders(ArgumentParser arguments, String configXml) {
        StringBuffer sb = new StringBuffer();
        Matcher m = Pattern.compile("\\$\\{(.+?)\\}").matcher(configXml);
        while (m.find()) {
            String key = m.group(1);
            String replacement = arguments.getOption(key);
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);
        return sb.toString();
    }

}
