package com.terraboxstudios.nanopay.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationTest {

    @Test
    void testConfigCreateAndLoad(@TempDir Path configFolder) throws IOException {
        Configuration configuration = new Configuration(configFolder);
        assertTrue(Files.exists(configuration.getConfigFile()));

        Properties loadedProperties = configuration.getProperties();
        Properties expectedProperties = new Properties();
        expectedProperties.load(getClass().getClassLoader()
                .getResourceAsStream(configuration.getConfigFileName()));
        assertEquals(expectedProperties, loadedProperties);
    }

}