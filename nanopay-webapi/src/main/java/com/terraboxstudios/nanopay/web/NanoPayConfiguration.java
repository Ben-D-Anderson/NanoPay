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
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

    public String getString(String key) {
        String env = System.getenv(key);
        if (env == null || env.strip().length() == 0)
            return this.properties.getProperty(key);
        return env;
    }

    public int getInt(String key) {
        return Integer.parseInt(getString(key));
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(getString(key));
    }
    
    public NanoPay createNanoPay(Consumer<String> paymentSuccessListener, Consumer<String> paymentFailureListener) {
        NanoPay.Builder builder = new NanoPay.Builder(getString("nanopay.storage_wallet"),
                paymentSuccessListener, paymentFailureListener);
        //representative
        if (getString("nanopay.representative_wallet") != null)
            builder.setRepresentativeWallet(getString("nanopay.representative_wallet"));
        //rpc
        if (getString("nanopay.rpc_address") != null)
            builder.setRpcAddress(getString("nanopay.rpc_address"));
        //websocket
        if (getString("nanopay.websocket_address") != null)
            builder.setWebSocketAddress(getString("nanopay.websocket_address"));
        //disable websocket reconnect
        if (getBoolean("nanopay.disable_websocket_reconnect"))
            builder.disableWebSocketReconnect();
        //disable wallet prune service
        if (getBoolean("nanopay.disable_wallet_prune_service"))
            builder.disableWalletPruneService();
        //disable wallet refund service
        if (getBoolean("nanopay.disable_wallet_refund_service"))
            builder.disableRefundService();
        //wallet prune delay
        if (getString("nanopay.delay.wallet_prune.initial_amount") != null
                && getString("nanopay.delay.wallet_prune.reoccurring_amount") != null
                && getString("nanopay.delay.wallet_prune.unit") != null) {
            int initialDelayAmount = getInt("nanopay.delay.wallet_prune.initial_amount");
            int reoccurringDelayAmount = getInt("nanopay.delay.wallet_prune.reoccurring_amount");
            TimeUnit delayUnit = TimeUnit.valueOf(getString("nanopay.delay.wallet_prune.unit").toUpperCase());
            builder.setWalletPruneDelay(initialDelayAmount, reoccurringDelayAmount, delayUnit);
        }
        //wallet refund delay
        if (getString("nanopay.delay.wallet_refund.initial_amount") != null
                && getString("nanopay.delay.wallet_refund.reoccurring_amount") != null
                && getString("nanopay.delay.wallet_refund.unit") != null) {
            int initialDelayAmount = getInt("nanopay.delay.wallet_refund.initial_amount");
            int reoccurringDelayAmount = getInt("nanopay.delay.wallet_refund.reoccurring_amount");
            TimeUnit delayUnit = TimeUnit.valueOf(getString("nanopay.delay.wallet_refund.unit").toUpperCase());
            builder.setWalletRefundDelay(initialDelayAmount, reoccurringDelayAmount, delayUnit);
        }
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

    Optional<WalletDeathLogger> parseWalletDeathLogger() {
        String prefix = "nanopay.deathlog.";
        String type = getString(prefix + "type");
        if (type == null)
            return Optional.empty();
        if (!type.equalsIgnoreCase("database") && !type.equalsIgnoreCase("hibernate"))
            return Optional.empty();
        String url = getString(prefix + "url");
        String driver = getString(prefix + "driver");
        String hbm2ddl = getString(prefix + "hbm2ddl");
        Configuration configuration = new Configuration()
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.connection.driver_class", driver)
                .setProperty("hibernate.hbm2ddl.auto", hbm2ddl);
        return Optional.of(new HibernateWalletDeathLogger(configuration));
    }

    Optional<WalletStorage> parseWalletStorage(WalletType walletType) {
        String prefix = "nanopay.storage." + walletType.toString().toLowerCase() + ".";
        String type = getString(prefix + "type");
        if (type == null)
            return Optional.empty();
        long durationAmount = getInt(prefix + "duration.amount");
        TemporalUnit durationUnit = ChronoUnit.valueOf(getString(prefix + "duration.unit").toUpperCase());
        Duration duration = Duration.of(durationAmount, durationUnit);
        WalletStorage walletStorage = null;
        switch (type.toLowerCase()) {
            case "database":
            case "hibernate":
                String url = getString(prefix + "url");
                String driver = getString(prefix + "driver");
                String hbm2ddl = getString(prefix + "hbm2ddl");
                Configuration configuration = new Configuration()
                        .setProperty("hibernate.connection.url", url)
                        .setProperty("hibernate.connection.driver_class", driver)
                        .setProperty("hibernate.hbm2ddl.auto", hbm2ddl);
                walletStorage = new HibernateWalletStorage(walletType, duration, configuration);
                break;
            case "memory":
                walletStorage = new MemoryWalletStorage(duration);
                break;
            case "file":
            case "singlefile":
            case "single_file":
                try {
                    walletStorage = new SingleFileWalletStorage(Paths.get(getString(prefix + "path")), duration);
                } catch (IOException e) {
                    NanoPay.LOGGER.error("Failed to create SingleFileWalletStorage", e);
                }
                break;
            case "files":
            case "multifile":
            case "multi_file":
            case "multiplefiles":
            case "multiple_files":
                try {
                    walletStorage = new MultipleFileWalletStorage(Paths.get(getString(prefix + "path")), duration);
                } catch (IOException e) {
                    NanoPay.LOGGER.error("Failed to create MultipleFileWalletStorage", e);
                }
                break;
            default:
                break;
        }
        if (walletStorage != null && getBoolean(prefix + "cache"))
            walletStorage = new CacheWrappedWalletStorage(walletStorage, Executors.newSingleThreadExecutor(),
                    CacheWrappedWalletStorage.CacheSearchPolicy.valueOf(getString(prefix + "cache.policy").toUpperCase()));
        return Optional.ofNullable(walletStorage);
    }

}