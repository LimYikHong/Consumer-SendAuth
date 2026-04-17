package com.worldline.mock.repository;

import com.worldline.mock.entity.KeyExchangeAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface KeyExchangeAuditRepository extends JpaRepository<KeyExchangeAudit, Long> {

    Page<KeyExchangeAudit> findAllByOrderByRequestedAtDesc(Pageable pageable);

    Page<KeyExchangeAudit> findByStatusOrderByRequestedAtDesc(String status, Pageable pageable);

    long countByStatus(String status);

    @Query("SELECT COUNT(k) FROM KeyExchangeAudit k")
    long countTotal();
}
