package com.terraboxstudios.nanopay.hibernate;

import com.terraboxstudios.nanopay.NanoPay;
import com.terraboxstudios.nanopay.death.RangeSearchable;
import com.terraboxstudios.nanopay.death.WalletDeathLogger;
import com.terraboxstudios.nanopay.hibernate.entity.DeadWalletEntity;
import com.terraboxstudios.nanopay.wallet.DeadWallet;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.query.Query;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public class HibernateWalletDeathLogger extends DatabaseAccessor implements WalletDeathLogger, RangeSearchable<DeadWallet, Instant> {

    public HibernateWalletDeathLogger(Configuration databaseConfiguration) {
        this(databaseConfiguration.addAnnotatedClass(DeadWalletEntity.class).buildSessionFactory());
    }

    HibernateWalletDeathLogger(SessionFactory databaseSessionFactory) {
        super(databaseSessionFactory);
    }

    @Override
    public void log(DeadWallet deadWallet) {
        createRunnable(session -> {
            session.beginTransaction();
            try {
                session.save(new DeadWalletEntity(deadWallet));
            } catch (HibernateException e) {
                NanoPay.LOGGER.error("Hibernate error occurred when saving dead wallet '" + deadWallet.address() + "'.", e);
            }
            session.getTransaction().commit();
        }).run();
    }

    @Override
    public List<DeadWallet> findByRange(Instant rangeLower, Instant rangeHigher) {
        Callable<Optional<List<DeadWalletEntity>>> findCallable = createCallable(session -> {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<DeadWalletEntity> cr = cb.createQuery(DeadWalletEntity.class);
            Root<DeadWalletEntity> root = cr.from(DeadWalletEntity.class);
            cr.select(root).where(cb.and(
                    cb.greaterThanOrEqualTo(root.get("deathTime"), rangeLower),
                    cb.lessThanOrEqualTo(root.get("deathTime"), rangeHigher)
            ));
            Query<DeadWalletEntity> query = session.createQuery(cr);
            return query.getResultList();
        });
        try {
            return findCallable.call().map(walletEntities ->
                    walletEntities.stream()
                            .map(DeadWalletEntity::asDeadWallet)
                            .sorted(Comparator.comparingLong(deadWallet ->
                                    ((DeadWallet) deadWallet).deathTime().toEpochMilli()).reversed())
                            .collect(Collectors.toList())
            ).orElse(Collections.emptyList());
        } catch (Exception e) {
            NanoPay.LOGGER.error("Hibernate error occurred when getting all dead wallets in range.", e);
            return Collections.emptyList();
        }
    }

}
