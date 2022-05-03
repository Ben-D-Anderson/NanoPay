package com.terraboxstudios.nanopay.hibernate;

import com.terraboxstudios.nanopay.hibernate.entity.WalletEntity;
import com.terraboxstudios.nanopay.storage.WalletType;
import com.terraboxstudios.nanopay.wallet.Wallet;
import lombok.SneakyThrows;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import uk.oczadly.karl.jnano.model.HexData;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.util.SecureRandomUtil;
import uk.oczadly.karl.jnano.util.WalletUtil;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.*;

class HibernateWalletStorageTest {

    Clock clock;
    SessionFactory sessionFactory;
    final BigDecimal REQUIRED_AMOUNT = new BigDecimal("5.0");

    @BeforeEach
    void setupDatabase() {
        java.util.logging.Logger.getLogger("org.hibernate").setLevel(Level.SEVERE);
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        Configuration configuration = new Configuration()
                .addAnnotatedClass(WalletEntity.class)
                .addAnnotatedClass(WalletEntity.WalletEntityId.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:testdb")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        sessionFactory = configuration.buildSessionFactory();
    }

    @SneakyThrows
    private Wallet generateTestWallet() {
        HexData privateKey = WalletUtil.generateRandomKey(SecureRandomUtil.getSecureRandom());
        return new Wallet(
                NanoAccount.fromPrivateKey(privateKey).toAddress(),
                privateKey.toString(),
                clock.instant(),
                REQUIRED_AMOUNT
        );
    }

    HibernateWalletStorage getHibernateWalletStorage(WalletType walletType) {
        return new HibernateWalletStorage(
                walletType,
                Duration.ofMinutes(1),
                sessionFactory
        );
    }

    @Test
    void getAllWallets() {
        Wallet walletOne = generateTestWallet();
        Wallet walletTwo = generateTestWallet();
        //insert two wallets into database
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            WalletEntity walletEntityOne = new WalletEntity(walletOne, WalletType.ACTIVE);
            session.save(walletEntityOne);
            WalletEntity walletEntityTwo = new WalletEntity(walletTwo, WalletType.ACTIVE);
            session.save(walletEntityTwo);
            session.getTransaction().commit();
        }
        //get wallets from wallet storage and ensure matches expected
        Collection<Wallet> expectedWallets = List.of(walletOne, walletTwo);
        try (HibernateWalletStorage walletStorage = getHibernateWalletStorage(WalletType.ACTIVE)) {
            List<Wallet> foundWallets = new LinkedList<>(walletStorage.getAllWallets());
            assertEquals(expectedWallets.size(), foundWallets.size());
            assertTrue(expectedWallets.containsAll(foundWallets));
            assertTrue(foundWallets.containsAll(expectedWallets));
        }
    }

    @Test
    void failFindWalletByAddressDifferentState() {
        Wallet wallet = generateTestWallet();
        //insert wallet into database with different active/dead state to wallet storage
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            WalletEntity walletEntity = new WalletEntity(wallet, WalletType.DEAD);
            session.save(walletEntity);
            session.getTransaction().commit();
        }
        //try to find wallet in database and find nothing as state is wrong
        try (HibernateWalletStorage walletStorage = getHibernateWalletStorage(WalletType.ACTIVE)) {
            Optional<Wallet> foundWalletOptional = walletStorage.findWalletByAddress(wallet.address());
            assertFalse(foundWalletOptional.isPresent());
        }
    }

    @Test
    void findWalletByAddress() {
        Wallet wallet = generateTestWallet();
        //insert wallet into database
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            WalletEntity walletEntity = new WalletEntity(wallet, WalletType.ACTIVE);
            session.save(walletEntity);
            session.getTransaction().commit();
        }
        //try to find wallet in wallet storage
        try (HibernateWalletStorage walletStorage = getHibernateWalletStorage(WalletType.ACTIVE)) {
            Optional<Wallet> foundWalletOptional = walletStorage.findWalletByAddress(wallet.address());
            assertTrue(foundWalletOptional.isPresent());
            assertEquals(wallet, foundWalletOptional.get());
        }
    }

    @Test
    void saveActiveWallet() {
        //try save a wallet in active storage
        saveWallet(generateTestWallet(), WalletType.ACTIVE);
    }

    @Test
    void saveDeadWallet() {
        //try save a wallet in dead storage
        saveWallet(generateTestWallet(), WalletType.DEAD);
    }

    @Test
    void failSaveDuplicateWalletDifferentState() {
        //try save two duplicate wallets but with different active/dead state and ensure an exception is thrown
        Wallet wallet = generateTestWallet();
        saveWallet(wallet, WalletType.ACTIVE);
        assertThrows(IllegalStateException.class, () -> saveWallet(wallet, WalletType.DEAD));
    }

    @Test
    void failSaveDuplicateWalletSameState() throws Throwable {
        //try save two duplicate wallets and ensure an exception is thrown
        Wallet wallet = generateTestWallet();
        Executable saveExecutable = () -> saveWallet(wallet, WalletType.ACTIVE);
        saveExecutable.execute();
        assertThrows(IllegalStateException.class, saveExecutable);
    }

    void saveWallet(Wallet wallet, WalletType walletType) {
        try (HibernateWalletStorage walletStorage = getHibernateWalletStorage(walletType)) {
            //save wallet into database
            walletStorage.saveWallet(wallet);
            try (Session session = sessionFactory.openSession()) {
                //check that wallet is in database
                WalletEntity walletEntity = session.get(WalletEntity.class,
                        new WalletEntity.WalletEntityId(wallet.address(), walletType));
                assertNotNull(walletEntity);
                assertEquals(walletType, walletEntity.getWalletEntityId().getWalletType());
                assertEquals(wallet, walletEntity.asWallet());
            }
        }
    }

    @Test
    void deleteWallet() {
        Wallet wallet = generateTestWallet();
        //insert wallet into database
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            WalletEntity walletEntity = new WalletEntity(wallet, WalletType.ACTIVE);
            session.save(walletEntity);
            session.getTransaction().commit();
        }
        try (HibernateWalletStorage walletStorage = getHibernateWalletStorage(WalletType.ACTIVE)) {
            //check wallet is in database
            try (Session session = sessionFactory.openSession()) {
                WalletEntity walletEntity = session.get(WalletEntity.class,
                        new WalletEntity.WalletEntityId(wallet.address(), WalletType.ACTIVE));
                assertNotNull(walletEntity);
            }
            //delete wallet
            walletStorage.deleteWallet(wallet);
            //check wallet is not in database
            try (Session session = sessionFactory.openSession()) {
                WalletEntity walletEntity = session.get(WalletEntity.class,
                        new WalletEntity.WalletEntityId(wallet.address(), WalletType.ACTIVE));
                assertNull(walletEntity);
            }
        }
    }

    @Test
    void failDeleteWalletDifferentState() {
        Wallet wallet = generateTestWallet();
        //insert wallet into database with opposite state to `activeStorage`
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            WalletEntity walletEntity = new WalletEntity(wallet, WalletType.DEAD);
            session.save(walletEntity);
            session.getTransaction().commit();
        }
        try (HibernateWalletStorage walletStorage = getHibernateWalletStorage(WalletType.ACTIVE)) {
            //check wallet is in database
            try (Session session = sessionFactory.openSession()) {
                WalletEntity walletEntity = session.get(WalletEntity.class,
                        new WalletEntity.WalletEntityId(wallet.address(), WalletType.DEAD));
                assertNotNull(walletEntity);
            }
            //shouldn't delete wallet as wrong active/dead state
            walletStorage.deleteWallet(wallet);
            //check wallet is still in database
            try (Session session = sessionFactory.openSession()) {
                WalletEntity walletEntity = session.get(WalletEntity.class,
                        new WalletEntity.WalletEntityId(wallet.address(), WalletType.DEAD));
                assertNotNull(walletEntity);
            }
        }
    }

}