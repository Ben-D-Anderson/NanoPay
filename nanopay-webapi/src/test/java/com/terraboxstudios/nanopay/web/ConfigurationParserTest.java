package com.terraboxstudios.nanopay.web;

import com.terraboxstudios.nanopay.death.WalletDeathLogger;
import com.terraboxstudios.nanopay.hibernate.HibernateWalletDeathLogger;
import com.terraboxstudios.nanopay.hibernate.HibernateWalletStorage;
import com.terraboxstudios.nanopay.storage.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class ConfigurationParserTest {

    private Configuration configuration;

    @BeforeEach
    void setup(@TempDir Path tempDir) {
        configuration = spy(new Configuration(tempDir));
    }

    @Test
    void testParseDatabaseWalletLogger() {
        doReturn(Optional.of("database")).when(configuration).getString("nanopay.deathlog.type");
        doReturn(Optional.of("jdbc:h2:mem:testdb")).when(configuration).getString("nanopay.deathlog.url");
        doReturn(Optional.of("org.h2.Driver")).when(configuration).getString("nanopay.deathlog.driver");
        doReturn(Optional.of("create-drop")).when(configuration).getString("nanopay.deathlog.hbm2ddl");

        ConfigurationParser configurationParser = new ConfigurationParser(configuration);
        Optional<WalletDeathLogger> walletDeathLoggerOptional = configurationParser.parseWalletDeathLogger();
        assertTrue(walletDeathLoggerOptional.isPresent());
        WalletDeathLogger walletDeathLogger = walletDeathLoggerOptional.get();
        assertInstanceOf(HibernateWalletDeathLogger.class, walletDeathLogger);
    }

    @Test
    void testParseDatabaseWalletStorage() {
        doReturn(Optional.of("database")).when(configuration).getString("nanopay.storage.active.type");
        doReturn(Optional.of("30")).when(configuration).getString("nanopay.storage.active.duration.amount");
        doReturn(Optional.of("minutes")).when(configuration).getString("nanopay.storage.active.duration.unit");
        doReturn(Optional.of("jdbc:h2:mem:testdb")).when(configuration).getString("nanopay.storage.active.url");
        doReturn(Optional.of("org.h2.Driver")).when(configuration).getString("nanopay.storage.active.driver");
        doReturn(Optional.of("create-drop")).when(configuration).getString("nanopay.storage.active.hbm2ddl");
        doReturn(Optional.of("false")).when(configuration).getString("nanopay.storage.active.cache");

        ConfigurationParser configurationParser = new ConfigurationParser(configuration);
        Optional<WalletStorage> walletStorageOptional = configurationParser.parseWalletStorage(WalletType.ACTIVE);
        assertTrue(walletStorageOptional.isPresent());
        WalletStorage walletStorage = walletStorageOptional.get();
        assertInstanceOf(HibernateWalletStorage.class, walletStorage);
        assertEquals(walletStorage.getWalletExpirationTime(), Duration.of(30, ChronoUnit.MINUTES));
    }

    @Test
    void testParseMemoryWalletStorage() {
        doReturn(Optional.of("memory")).when(configuration).getString("nanopay.storage.active.type");
        doReturn(Optional.of("30")).when(configuration).getString("nanopay.storage.active.duration.amount");
        doReturn(Optional.of("minutes")).when(configuration).getString("nanopay.storage.active.duration.unit");
        doReturn(Optional.of("false")).when(configuration).getString("nanopay.storage.active.cache");

        ConfigurationParser configurationParser = new ConfigurationParser(configuration);
        Optional<WalletStorage> walletStorageOptional = configurationParser.parseWalletStorage(WalletType.ACTIVE);
        assertTrue(walletStorageOptional.isPresent());
        WalletStorage walletStorage = walletStorageOptional.get();
        assertInstanceOf(MemoryWalletStorage.class, walletStorage);
        assertEquals(walletStorage.getWalletExpirationTime(), Duration.of(30, ChronoUnit.MINUTES));
    }

    @Test
    void testParseSingleFileWalletStorage(@TempDir Path tempDir) throws IOException {
        Path tempFile = Files.createFile(tempDir.resolve("wallet_storage.json"));

        doReturn(Optional.of("single_file")).when(configuration).getString("nanopay.storage.active.type");
        doReturn(Optional.of(tempFile.toString())).when(configuration).getString("nanopay.storage.active.path");
        doReturn(Optional.of("30")).when(configuration).getString("nanopay.storage.active.duration.amount");
        doReturn(Optional.of("minutes")).when(configuration).getString("nanopay.storage.active.duration.unit");
        doReturn(Optional.of("false")).when(configuration).getString("nanopay.storage.active.cache");

        ConfigurationParser configurationParser = new ConfigurationParser(configuration);
        Optional<WalletStorage> walletStorageOptional = configurationParser.parseWalletStorage(WalletType.ACTIVE);
        assertTrue(walletStorageOptional.isPresent());
        WalletStorage walletStorage = walletStorageOptional.get();
        assertInstanceOf(SingleFileWalletStorage.class, walletStorage);
        assertEquals(walletStorage.getWalletExpirationTime(), Duration.of(30, ChronoUnit.MINUTES));
    }

    @Test
    void testParseMultiFileWalletStorage(@TempDir Path tempDir) {
        doReturn(Optional.of("multi_file")).when(configuration).getString("nanopay.storage.active.type");
        doReturn(Optional.of(tempDir.toString())).when(configuration).getString("nanopay.storage.active.path");
        doReturn(Optional.of("30")).when(configuration).getString("nanopay.storage.active.duration.amount");
        doReturn(Optional.of("minutes")).when(configuration).getString("nanopay.storage.active.duration.unit");
        doReturn(Optional.of("false")).when(configuration).getString("nanopay.storage.active.cache");

        ConfigurationParser configurationParser = new ConfigurationParser(configuration);
        Optional<WalletStorage> walletStorageOptional = configurationParser.parseWalletStorage(WalletType.ACTIVE);
        assertTrue(walletStorageOptional.isPresent());
        WalletStorage walletStorage = walletStorageOptional.get();
        assertInstanceOf(MultipleFileWalletStorage.class, walletStorage);
        assertEquals(walletStorage.getWalletExpirationTime(), Duration.of(30, ChronoUnit.MINUTES));
    }


    @Test
    void testParseCachedWalletStorage() {
        doReturn(Optional.of("memory")).when(configuration).getString("nanopay.storage.active.type");
        doReturn(Optional.of("30")).when(configuration).getString("nanopay.storage.active.duration.amount");
        doReturn(Optional.of("minutes")).when(configuration).getString("nanopay.storage.active.duration.unit");
        doReturn(Optional.of("true")).when(configuration).getString("nanopay.storage.active.cache");
        doReturn(Optional.of("BACKING_IF_MISS")).when(configuration).getString("nanopay.storage.active.cache.policy");

        ConfigurationParser configurationParser = new ConfigurationParser(configuration);
        Optional<WalletStorage> walletStorageOptional = configurationParser.parseWalletStorage(WalletType.ACTIVE);
        assertTrue(walletStorageOptional.isPresent());
        WalletStorage walletStorage = walletStorageOptional.get();
        assertInstanceOf(CacheWrappedWalletStorage.class, walletStorage);
        assertEquals(CacheWrappedWalletStorage.CacheSearchPolicy.BACKING_IF_MISS,
                ((CacheWrappedWalletStorage) walletStorage).getCachePolicy());
    }


}