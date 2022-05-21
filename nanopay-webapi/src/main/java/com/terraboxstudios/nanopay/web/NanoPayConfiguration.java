package com.terraboxstudios.nanopay.web;

import com.terraboxstudios.nanopay.NanoPay;
import com.terraboxstudios.nanopay.death.DefaultWalletDeathLogger;
import com.terraboxstudios.nanopay.death.WalletDeathLogger;
import com.terraboxstudios.nanopay.hibernate.HibernateWalletDeathLogger;
import com.terraboxstudios.nanopay.hibernate.HibernateWalletStorage;
import com.terraboxstudios.nanopay.storage.*;
import lombok.Getter;
import org.hibernate.cfg.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Getter
public class NanoPayConfiguration {

    private final Properties properties;
    private final String configFileName = "nanopay.properties";
    private final Path configFile;

    public NanoPayConfiguration(Path configFolder) {
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
    
    public NanoPay createNanoPay(Consumer<String> paymentSuccessListener, Consumer<String> paymentFailureListener) {
        NanoPay.Builder builder = new NanoPay.Builder(getRequiredString("nanopay.storage_wallet"),
                paymentSuccessListener, paymentFailureListener);
        //representative
        builder.setRepresentativeWallet(getRequiredString("nanopay.representative_wallet"));
        //rpc address
        builder.setRpcAddress(getRequiredString("nanopay.rpc_address"));
        //websocket
        builder.setWebSocketAddress(getRequiredString("nanopay.websocket_address"));
        //disable websocket reconnect
        if (getBoolean("nanopay.disable_websocket_reconnect", false))
            builder.disableWebSocketReconnect();
        //disable wallet prune service
        if (getBoolean("nanopay.disable_wallet_prune_service", false))
            builder.disableWalletPruneService();
        //disable wallet refund service
        if (getBoolean("nanopay.disable_wallet_refund_service", false))
            builder.disableRefundService();
        //wallet prune delay
        builder.setWalletPruneDelay(parseRepeatingDelay("nanopay.delay.wallet_prune."));
        //wallet refund delay
        builder.setWalletRefundDelay(parseRepeatingDelay("nanopay.delay.wallet_refund."));
        //wallet storages
        WalletStorage activeStorage = parseWalletStorage(WalletType.ACTIVE)
                .orElse(new MemoryWalletStorage(Duration.ofMinutes(30)));
        WalletStorage deadStorage = parseWalletStorage(WalletType.DEAD)
                .orElse(new MemoryWalletStorage(Duration.ofMinutes(60)));
        builder.setWalletStorageProvider(new WalletStorageProvider(activeStorage, deadStorage));
        //wallet death logger
        builder.setWalletDeathLogger(parseWalletDeathLogger().orElse(new DefaultWalletDeathLogger()));

        return builder.build();
    }

    NanoPay.RepeatingDelay parseRepeatingDelay(String prefix) {
        int initialDelayAmount = getRequiredInt(prefix + "initial_amount");
        int repeatingDelayAmount = getRequiredInt(prefix + "repeating_amount");
        TimeUnit delayUnit = TimeUnit.valueOf(getRequiredString(prefix + "unit"));
        return new NanoPay.RepeatingDelay(initialDelayAmount, repeatingDelayAmount, delayUnit);
    }

    Configuration parseDatabase(String prefix) {
        String url = getRequiredString(prefix + "url");
        String driver = getRequiredString(prefix + "driver");
        String hbm2ddl = getRequiredString(prefix + "hbm2ddl");
        return new Configuration()
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.connection.driver_class", driver)
                .setProperty("hibernate.hbm2ddl.auto", hbm2ddl);
    }

    Optional<WalletDeathLogger> parseWalletDeathLogger() {
        String prefix = "nanopay.deathlog.";
        Optional<String> typeOptional = getString(prefix + "type");
        if (typeOptional.isEmpty())
            return Optional.empty();
        String type = typeOptional.get();
        if (!type.equalsIgnoreCase("database") && !type.equalsIgnoreCase("hibernate"))
            return Optional.empty();
        return Optional.of(new HibernateWalletDeathLogger(parseDatabase(prefix)));
    }

    Optional<WalletStorage> parseWalletStorage(WalletType walletType) {
        String prefix = "nanopay.storage." + walletType.toString().toLowerCase() + ".";
        Optional<String> typeOptional = getString(prefix + "type");
        if (typeOptional.isEmpty())
            return Optional.empty();
        String type = typeOptional.get();
        long durationAmount = getRequiredInt(prefix + "duration.amount");
        TemporalUnit durationUnit = ChronoUnit.valueOf(getRequiredString(prefix + "duration.unit").toUpperCase());
        Duration duration = Duration.of(durationAmount, durationUnit);
        WalletStorage walletStorage = null;
        switch (type.toLowerCase()) {
            case "database":
            case "hibernate":
                walletStorage = new HibernateWalletStorage(walletType, duration, parseDatabase(prefix));
                break;
            case "memory":
                walletStorage = new MemoryWalletStorage(duration);
                break;
            case "file":
            case "singlefile":
            case "single_file":
                try {
                    walletStorage = new SingleFileWalletStorage(Paths.get(getRequiredString(prefix + "path")), duration);
                } catch (IOException e) {
                    NanoPay.LOGGER.error("Failed to create SingleFileWalletStorage", e);
                }
                break;
            case "files":
            case "multifile":
            case "multi_file":
            case "multiple_file":
            case "multiplefiles":
            case "multiple_files":
                try {
                    walletStorage = new MultipleFileWalletStorage(Paths.get(getRequiredString(prefix + "path")), duration);
                } catch (IOException e) {
                    NanoPay.LOGGER.error("Failed to create MultipleFileWalletStorage", e);
                }
                break;
            default:
                break;
        }
        if (walletStorage != null && getBoolean(prefix + "cache", false))
            walletStorage = new CacheWrappedWalletStorage(walletStorage, Executors.newSingleThreadExecutor(),
                    CacheWrappedWalletStorage.CacheSearchPolicy.valueOf(getRequiredString(prefix + "cache.policy").toUpperCase()));
        return Optional.ofNullable(walletStorage);
    }

}