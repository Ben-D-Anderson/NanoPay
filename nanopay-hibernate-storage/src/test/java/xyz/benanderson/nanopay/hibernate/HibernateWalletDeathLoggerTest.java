package xyz.benanderson.nanopay.hibernate;

import xyz.benanderson.nanopay.hibernate.entity.DeadWalletEntity;
import xyz.benanderson.nanopay.wallet.DeadWallet;
import xyz.benanderson.nanopay.wallet.SecureRandomUtil;
import lombok.SneakyThrows;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.oczadly.karl.jnano.model.HexData;
import uk.oczadly.karl.jnano.model.NanoAccount;
import uk.oczadly.karl.jnano.util.WalletUtil;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HibernateWalletDeathLoggerTest {

    Clock clock;
    SessionFactory sessionFactory;
    final BigDecimal REQUIRED_AMOUNT = new BigDecimal("5.0");

    @BeforeEach
    void setupDatabase() {
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        Configuration configuration = new Configuration()
                .addAnnotatedClass(DeadWalletEntity.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:testdb")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        sessionFactory = configuration.buildSessionFactory();
    }

    @SneakyThrows
    private DeadWallet generateTestDeadWallet(Instant deathTime, boolean success) {
        HexData privateKey = WalletUtil.generateRandomKey(SecureRandomUtil.getSecureRandom());
        return new DeadWallet(
                NanoAccount.fromPrivateKey(privateKey).toAddress(),
                privateKey.toString(),
                deathTime,
                REQUIRED_AMOUNT,
                success
        );
    }

    HibernateWalletDeathLogger getHibernateWalletDeathLogger() {
        return new HibernateWalletDeathLogger(sessionFactory);
    }

    @Test
    void log() {
        //create dead wallets
        DeadWallet deadWalletOne = generateTestDeadWallet(clock.instant(), false);
        DeadWallet deadWalletTwo = generateTestDeadWallet(clock.instant(), true);
        //log dead wallets
        try (HibernateWalletDeathLogger deathLogger = getHibernateWalletDeathLogger()) {
            deathLogger.log(deadWalletOne);
            deathLogger.log(deadWalletTwo);
            try (Session session = sessionFactory.openSession()) {
                //check that dead wallets are in log database
                DeadWalletEntity deadWalletEntityOne = session.get(DeadWalletEntity.class, deadWalletOne.address());
                DeadWalletEntity deadWalletEntityTwo = session.get(DeadWalletEntity.class, deadWalletTwo.address());
                assertNotNull(deadWalletEntityOne);
                assertNotNull(deadWalletEntityTwo);
                assertEquals(deadWalletOne, deadWalletEntityOne.asDeadWallet());
                assertEquals(deadWalletTwo, deadWalletEntityTwo.asDeadWallet());
            }
        }
    }

    @Test
    void findByRange() {
        //define search range
        Instant rangeLower = Instant.ofEpochMilli(clock.millis() - 1000L);
        Instant rangeHigher = Instant.ofEpochMilli(clock.millis() + 1000L);
        //create test dead wallets
        DeadWallet deadWalletOne = generateTestDeadWallet(rangeLower.minusSeconds(1), false);
        DeadWallet deadWalletTwo = generateTestDeadWallet(rangeLower, false);
        DeadWallet deadWalletThree = generateTestDeadWallet(clock.instant(), false);
        DeadWallet deadWalletFour = generateTestDeadWallet(rangeHigher, false);
        DeadWallet deadWalletFive = generateTestDeadWallet(rangeHigher.plusSeconds(1), false);
        //insert dead wallets into database
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.persist(new DeadWalletEntity(deadWalletOne));
            session.persist(new DeadWalletEntity(deadWalletTwo));
            session.persist(new DeadWalletEntity(deadWalletThree));
            session.persist(new DeadWalletEntity(deadWalletFour));
            session.persist(new DeadWalletEntity(deadWalletFive));
            session.getTransaction().commit();
        }
        //check wallets retrieved by range are correct
        List<DeadWallet> walletsToFind = List.of(deadWalletTwo, deadWalletThree, deadWalletFour);
        try (HibernateWalletDeathLogger deathLogger = getHibernateWalletDeathLogger()) {
            List<DeadWallet> foundWallets = deathLogger.findByRange(rangeLower, rangeHigher);
            assertEquals(walletsToFind.size(), foundWallets.size());
            assertTrue(walletsToFind.containsAll(foundWallets));
            assertTrue(foundWallets.containsAll(walletsToFind));
        }
    }
}