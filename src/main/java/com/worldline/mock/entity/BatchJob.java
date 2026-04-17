package com.worldline.mock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Tracks each batch request received from Kafka. One batch job = one encrypted
 * CSV file processed.
 */
@Entity
@Table(name = "batch_job", indexes = {
    @Index(name = "idx_batch_job_status", columnList = "status"),
    @Index(name = "idx_batch_job_batch_id", columnList = "batchId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique batch ID from the producer system
     */
    @Column(nullable = false, unique = true, length = 64)
    private String batchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private BatchStatus status;

    private int totalRecords;
    private int approvedCount;
    private int declinedCount;

    @Column(updatable = false)
    private LocalDateTime receivedAt;

    private LocalDateTime completedAt;

    /**
     * Error message if processing failed
     */
    @Column(length = 1000)
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        this.receivedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = BatchStatus.RECEIVED;
        }
    }
}
