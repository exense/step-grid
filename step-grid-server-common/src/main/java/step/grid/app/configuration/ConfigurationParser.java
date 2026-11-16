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
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a YAML or JSON configuration file. The placeholders of the file are replaced by the startup argument of the
 * same name (case-insensitive) or, if no argument defines them, by the environment variable of the same name
 * (case-sensitive).
 */
public class ConfigurationParser<T extends AppConfiguration> {

    private static final String JSON = "json";
    private static final String YAML = "yaml";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{(.+?)\\}");

    private final Function<String, String> environmentVariables;

    public ConfigurationParser() {
        this(System::getenv);
    }

    ConfigurationParser(Function<String, String> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    public T parse(ArgumentParser arguments, File file, Class<T> clazz) throws Exception {
        Objects.requireNonNull(arguments, "The arguments must not be null");
        Objects.requireNonNull(file, "The configuration file must not be null");
        Objects.requireNonNull(clazz, "The configuration class must not be null");
        byte[] bytes = Files.readAllBytes(file.toPath());

        String content = new String(bytes);

        String resolvedContent = replacePlaceholders(arguments, content);

        ObjectMapper mapper;
        if (file.getName().endsWith(YAML)) {
            mapper = new ObjectMapper(new YAMLFactory());
        } else if (file.getName().endsWith(JSON)) {
            mapper = new ObjectMapper();
        } else {
            throw new IllegalArgumentException("Unsupported file type for agent configuration: "
                + file.getAbsolutePath() + ". Supported file types are .yaml and .json");
        }

        return mapper.readValue(resolvedContent, clazz);
    }

    private String replacePlaceholders(ArgumentParser arguments, String content) {
        StringBuilder sb = new StringBuilder();
        Matcher m = PLACEHOLDER_PATTERN.matcher(content);
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(getPlaceholderValue(arguments, m.group(1))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String getPlaceholderValue(ArgumentParser arguments, String key) {
        String value = arguments.getOption(key);
        if (value == null) {
            value = environmentVariables.apply(key);
        }
        if (value == null) {
            throw new IllegalArgumentException("Missing placeholder '" + key + "': not defined as argument nor as environment variable.");
        }
        return value;
    }

}
