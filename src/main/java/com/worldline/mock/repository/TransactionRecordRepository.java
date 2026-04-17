package com.worldline.mock.repository;

import com.worldline.mock.entity.AuthorizationResult;
import com.worldline.mock.entity.TransactionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, Long> {

    List<TransactionRecord> findByBatchId(String batchId);

    Page<TransactionRecord> findByBatchIdOrderByIdAsc(String batchId, Pageable pageable);

    Page<TransactionRecord> findByBatchIdAndAuthResultOrderByIdAsc(
            String batchId, AuthorizationResult authResult, Pageable pageable);

    @Query("SELECT COUNT(t) FROM TransactionRecord t WHERE t.batchId = :batchId AND t.authResult = :result")
    long countByBatchIdAndResult(@Param("batchId") String batchId, @Param("result") AuthorizationResult result);

    long countByBatchId(String batchId);
}
