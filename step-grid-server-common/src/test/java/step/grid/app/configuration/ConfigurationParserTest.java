/*******************************************************************************
 * Copyright (C) 2026, exense GmbH
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
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ConfigurationParserTest {

    private static final Map<String, String> ENVIRONMENT_VARIABLES = Map.of(
        "KEYSTORE_PATH", "fromEnvironment",
        "KEYSTORE_PASSWORD", "fromEnvironment",
        "keyManagerPassword", "fromEnvironment");

    private final ConfigurationParser<AppConfiguration> parser = new ConfigurationParser<>(ENVIRONMENT_VARIABLES::get);

    @Test
    public void placeholdersFromArgumentsAndEnvironmentVariables() throws Exception {
        File file = configurationFile(".yaml", "keyStorePath: ${KEYSTORE_PATH}\n" +
            "keyStorePassword: ${KEYSTORE_PASSWORD}\n" +
            "keyManagerPassword: ${keyManagerPassword}\n");
        ArgumentParser arguments = new ArgumentParser(new String[]{"-keystore_password=fromArgument","-keyManagerPassword=fromArgument$\\"});

        // The arguments (case-insensitive) take precedence, the environment variables are used for the placeholders
        // missing from them
        AppConfiguration configuration = parser.parse(arguments, file, AppConfiguration.class);
        assertEquals("fromEnvironment", configuration.getKeyStorePath());
        assertEquals("fromArgument", configuration.getKeyStorePassword());
        // Special characters of the regular expression replacements are kept as they are
        assertEquals("fromArgument$\\", configuration.getKeyManagerPassword());
    }

    @Test
    public void placeholdersInJson() throws Exception {
        File file = configurationFile(".json", "{\"keyStorePath\": \"${KEYSTORE_PATH}\"}");

        AppConfiguration configuration = parser.parse(new ArgumentParser(new String[]{}), file, AppConfiguration.class);
        assertEquals("fromEnvironment", configuration.getKeyStorePath());
    }

    @Test
    public void missingPlaceholder() throws Exception {
        // The environment variables are case-sensitive
        File file = configurationFile(".yaml", "keyStorePath: ${keystore_path}\n");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> parser.parse(new ArgumentParser(new String[]{}), file, AppConfiguration.class));
        assertEquals("Missing placeholder 'keystore_path': not defined as argument nor as environment variable.", e.getMessage());
    }

    private static File configurationFile(String extension, String content) throws IOException {
        File file = File.createTempFile("ConfigurationParserTest", extension);
        file.deleteOnExit();
        Files.writeString(file.toPath(), content);
        return file;
    }
}
