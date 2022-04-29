package com.terraboxstudios.nanopay.web;

import com.terraboxstudios.nanopay.NanoPay;
import lombok.Getter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;

public class NanoPayConfiguration {

    @Getter
    private final Properties properties;
    private final String configFileName = "nanopay.properties";
    private final Path configFile;

    public NanoPayConfiguration() {
        configFile = Paths.get(System.getProperty("user.dir")).resolve(configFileName);
        Properties internalProperties = new Properties();
        loadInternalProperties(internalProperties);
        this.properties = new Properties(internalProperties);
        if (createFileIfNotExists()) {
            loadFromFile();
        }
    }

    private void loadInternalProperties(Properties internalProperties) {
        try {
            internalProperties.load(getClass().getClassLoader().getResourceAsStream(configFileName));
        } catch (IOException e) {
            NanoPay.LOGGER.error("Failed to read internal configuration file.", e);
        }
    }

    private void loadFromFile() {
        try (InputStream inputStream = Files.newInputStream(configFile)) {
            this.properties.load(inputStream);
        } catch (IOException e) {
            NanoPay.LOGGER.error("Failed to read external configuration file - resorting to internal configuration.", e);
        }
    }

    /**
     * @return whether configuration should be loaded from the file
     */
    private boolean createFileIfNotExists() {
        if (!Files.exists(configFile)) {
            try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(configFileName)) {
                Files.copy(Objects.requireNonNull(inputStream), configFile);
            } catch (Exception e) {
                NanoPay.LOGGER.error("Failed to write default config to configuration file '" + configFile + "'.", e);
                return false;
            }
        }
        return true;
    }

    public String getString(String key) {
        return this.properties.getProperty(key);
    }

    public int getInt(String key) {
        return Integer.parseInt(getString(key));
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(getString(key));
    }

}