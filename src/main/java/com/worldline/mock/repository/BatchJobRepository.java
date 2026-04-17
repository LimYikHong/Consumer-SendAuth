package com.worldline.mock.repository;

import com.worldline.mock.entity.BatchJob;
import com.worldline.mock.entity.BatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BatchJobRepository extends JpaRepository<BatchJob, Long> {

    Optional<BatchJob> findByBatchId(String batchId);

    boolean existsByBatchId(String batchId);

    Page<BatchJob> findAllByOrderByReceivedAtDesc(Pageable pageable);

    Page<BatchJob> findByStatusOrderByReceivedAtDesc(BatchStatus status, Pageable pageable);
}
