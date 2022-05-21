package com.terraboxstudios.nanopay.web;

import com.terraboxstudios.nanopay.NanoPay;
import com.terraboxstudios.nanopay.death.DefaultWalletDeathLogger;
import com.terraboxstudios.nanopay.death.WalletDeathLogger;
import com.terraboxstudios.nanopay.hibernate.HibernateWalletDeathLogger;
import com.terraboxstudios.nanopay.hibernate.HibernateWalletStorage;
import com.terraboxstudios.nanopay.storage.*;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ConfigurationParser {

    private final Configuration configuration;

    public ConfigurationParser(Configuration configuration) {
        this.configuration = configuration;
    }

    public NanoPay createNanoPay(Consumer<String> paymentSuccessListener, Consumer<String> paymentFailureListener) {
        NanoPay.Builder builder = new NanoPay.Builder(configuration.getRequiredString("nanopay.storage_wallet"),
                paymentSuccessListener, paymentFailureListener);
        //representative
        builder.setRepresentativeWallet(configuration.getRequiredString("nanopay.representative_wallet"));
        //rpc address
        builder.setRpcAddress(configuration.getRequiredString("nanopay.rpc_address"));
        //websocket
        builder.setWebSocketAddress(configuration.getRequiredString("nanopay.websocket_address"));
        //disable websocket reconnect
        if (configuration.getBoolean("nanopay.disable_websocket_reconnect", false))
            builder.disableWebSocketReconnect();
        //disable wallet prune service
        if (configuration.getBoolean("nanopay.disable_wallet_prune_service", false))
            builder.disableWalletPruneService();
        //disable wallet refund service
        if (configuration.getBoolean("nanopay.disable_wallet_refund_service", false))
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
        int initialDelayAmount = configuration.getRequiredInt(prefix + "initial_amount");
        int repeatingDelayAmount = configuration.getRequiredInt(prefix + "repeating_amount");
        TimeUnit delayUnit = TimeUnit.valueOf(configuration.getRequiredString(prefix + "unit"));
        return new NanoPay.RepeatingDelay(initialDelayAmount, repeatingDelayAmount, delayUnit);
    }

    org.hibernate.cfg.Configuration parseDatabase(String prefix) {
        String url = configuration.getRequiredString(prefix + "url");
        String driver = configuration.getRequiredString(prefix + "driver");
        String hbm2ddl = configuration.getRequiredString(prefix + "hbm2ddl");
        return new org.hibernate.cfg.Configuration()
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.connection.driver_class", driver)
                .setProperty("hibernate.hbm2ddl.auto", hbm2ddl);
    }

    Optional<WalletDeathLogger> parseWalletDeathLogger() {
        String prefix = "nanopay.deathlog.";
        Optional<String> typeOptional = configuration.getString(prefix + "type");
        if (typeOptional.isEmpty())
            return Optional.empty();
        String type = typeOptional.get();
        if (!type.equalsIgnoreCase("database") && !type.equalsIgnoreCase("hibernate"))
            return Optional.empty();
        return Optional.of(new HibernateWalletDeathLogger(parseDatabase(prefix)));
    }

    Optional<WalletStorage> parseWalletStorage(WalletType walletType) {
        String prefix = "nanopay.storage." + walletType.toString().toLowerCase() + ".";
        Optional<String> typeOptional = configuration.getString(prefix + "type");
        if (typeOptional.isEmpty())
            return Optional.empty();
        String type = typeOptional.get();
        long durationAmount = configuration.getRequiredInt(prefix + "duration.amount");
        TemporalUnit durationUnit = ChronoUnit.valueOf(configuration.getRequiredString(prefix + "duration.unit").toUpperCase());
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
                    walletStorage = new SingleFileWalletStorage(Paths.get(configuration.getRequiredString(prefix + "path")), duration);
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
                    walletStorage = new MultipleFileWalletStorage(Paths.get(configuration.getRequiredString(prefix + "path")), duration);
                } catch (IOException e) {
                    NanoPay.LOGGER.error("Failed to create MultipleFileWalletStorage", e);
                }
                break;
            default:
                break;
        }
        if (walletStorage != null && configuration.getBoolean(prefix + "cache", false))
            walletStorage = new CacheWrappedWalletStorage(walletStorage, Executors.newSingleThreadExecutor(),
                    CacheWrappedWalletStorage.CacheSearchPolicy.valueOf(configuration.getRequiredString(prefix + "cache.policy").toUpperCase()));
        return Optional.ofNullable(walletStorage);
    }

}
