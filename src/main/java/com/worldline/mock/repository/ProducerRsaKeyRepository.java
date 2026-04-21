package com.worldline.mock.repository;

import com.worldline.mock.entity.ProducerRsaKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProducerRsaKeyRepository extends JpaRepository<ProducerRsaKey, Long> {

    /**
     * Get the latest active key
     */
    Optional<ProducerRsaKey> findTopByStatusOrderByFetchedAtDesc(String status);

    /**
     * Get the most recent key regardless of status
     */
    Optional<ProducerRsaKey> findTopByOrderByFetchedAtDesc();
}
