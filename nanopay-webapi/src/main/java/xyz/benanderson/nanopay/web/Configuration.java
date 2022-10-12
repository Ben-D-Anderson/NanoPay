package xyz.benanderson.nanopay.web;

import xyz.benanderson.nanopay.NanoPay;
import lombok.Getter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class Configuration {

    private final Properties properties;
    private final String configFileName = "nanopay.properties";
    private final Path configFile;

    public Configuration(Path configFolder) {
        configFile = configFolder.resolve(configFileName);
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

    public Optional<String> getString(String key) {
        String env = System.getenv(key);
        if (env == null || env.strip().length() == 0)
            return Optional.ofNullable(this.properties.getProperty(key));
        return Optional.of(env);
    }

    public String getRequiredString(String key) {
        return getString(key).orElseThrow(() -> new NoSuchElementException("'" + key + "' cannot be empty"));
    }

    public Optional<Integer> getInt(String key) {
        AtomicInteger valInt = new AtomicInteger();
        try {
            getString(key).ifPresent(val -> valInt.set(Integer.parseInt(val)));
        } catch (NumberFormatException ignored) {}
        return Optional.of(valInt.get());
    }

    public int getRequiredInt(String key) {
        return getInt(key).orElseThrow(() -> new NoSuchElementException("'" + key + "' must be an integer"));
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        AtomicBoolean valBool = new AtomicBoolean(defaultValue);
        getString(key).ifPresent(val -> valBool.set(Boolean.parseBoolean(val)));
        return valBool.get();
    }

}