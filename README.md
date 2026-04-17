# Consumer Return Auth — Mock Authorization Service

A **headless** (no UI, no login) Spring Boot 3 service that acts as a **mock third-party authorization system**. It listens for encrypted batch CSV files via Kafka, evaluates each transaction using rule-based logic, and publishes the results back.

## Architecture

```
[Batch Service (Angular 19 + Spring Boot)]
   ↓ publishes encrypted CSV
Kafka Topic: batch-request
   ↓
[THIS SERVICE — Mock Authorization]
   ↓ publishes results
Kafka Topic: batch-response
   ↓
[Transaction Update Service]
```

## Authorization Rules

| Condition | Result |
|-----------|--------|
| Account status = `BLOCKED` | **DECLINED** |
| Amount < 500 | **APPROVED** |
| Amount ≥ 500 | **RANDOM** (50/50 APPROVED/DECLINED) |

## Tech Stack

- **Java 17** + **Spring Boot 3.2.5**
- **Spring Kafka** — consumer/producer
- **Spring Data JPA** + **H2** (dev) / **PostgreSQL** (prod)
- **OpenCSV** — CSV parsing
- **AES Encryption** — decrypt incoming CSV (matches producer's key)
- **Hibernate JDBC batching** — efficient bulk inserts

## Kafka Message Format

### `batch-request` (incoming)
```json
{
  "batchId": "BATCH-20260415-001",
  "encryptedCsvContent": "<Base64-encoded AES-encrypted CSV>",
  "iv": "<Base64-encoded IV, optional for CBC mode>",
  "timestamp": "2026-04-15T10:00:00"
}
```

### CSV Content (after decryption)
```csv
transactionId,accountNumber,accountStatus,amount,currency,merchantName,merchantCategory
TXN-001,ACC-12345,ACTIVE,150.00,MYR,Merchant A,RETAIL
TXN-002,ACC-67890,BLOCKED,800.00,MYR,Merchant B,FOOD
TXN-003,ACC-11111,ACTIVE,600.00,MYR,Merchant C,TRAVEL
```

### `batch-response` (outgoing)
```json
{
  "batchId": "BATCH-20260415-001",
  "status": "COMPLETED",
  "totalRecords": 3,
  "approvedCount": 1,
  "declinedCount": 2,
  "processedAt": "2026-04-15T10:00:05",
  "results": [
    { "transactionId": "TXN-001", "accountNumber": "ACC-12345", "amount": "150.00", "authResult": "APPROVED", "decisionReason": "Amount 150.00 is below threshold 500" },
    { "transactionId": "TXN-002", "accountNumber": "ACC-67890", "amount": "800.00", "authResult": "DECLINED", "decisionReason": "Account status is BLOCKED" },
    { "transactionId": "TXN-003", "accountNumber": "ACC-11111", "amount": "600.00", "authResult": "DECLINED", "decisionReason": "Amount 600.00 >= 500 — randomly DECLINED" }
  ]
}
```

## Quick Start

### 1. Start Kafka
```bash
docker-compose up -d
```

### 2. Configure Encryption Key
In `application.yml`, set `app.crypto.secret-key` to match the Batch Service's AES key (Base64-encoded, 16 bytes for AES-128).

### 3. Run the Service
```bash
mvn spring-boot:run
```
The service starts on port **8881** and immediately listens to the `batch-request` Kafka topic.

### 4. Verify
- H2 Console: http://localhost:8881/h2-console (JDBC URL: `jdbc:h2:file:./data/mock-auth-db`)
- Kafka UI: http://localhost:8090

## Database Design

### `batch_job` — tracks each batch lifecycle
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-increment |
| batch_id | VARCHAR(64) UNIQUE | From producer |
| status | ENUM | RECEIVED → PROCESSING → COMPLETED/FAILED |
| total_records | INT | Count of CSV rows |
| approved_count | INT | Count of APPROVED |
| declined_count | INT | Count of DECLINED |
| received_at | TIMESTAMP | When batch was received |
| completed_at | TIMESTAMP | When processing finished |

### `transaction_record` — individual authorization decisions
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-increment |
| batch_id | VARCHAR(64) | FK to batch |
| transaction_id | VARCHAR(64) | From CSV |
| account_number | VARCHAR(32) | From CSV |
| account_status | ENUM | ACTIVE/BLOCKED/SUSPENDED |
| amount | DECIMAL(15,2) | Transaction amount |
| auth_result | ENUM | APPROVED/DECLINED |
| decision_reason | VARCHAR(200) | Why this decision |

**Indexes** optimized for:
- Batch lookups (`batch_id`)
- Account queries (`account_number`)
- Result filtering (`auth_result`)
- Composite batch+result queries

## Integration with Producer System

The producer system (Angular 19.2.15 + Spring Boot on ports 4300/8880) should:

1. **Encrypt** the grouped transaction CSV using the shared AES key
2. **Publish** a `BatchRequestMessage` to `batch-request` topic
3. **Listen** on `batch-response` topic for `BatchResponseMessage`
4. **Update** transaction statuses based on `authResult` per record

## Project Structure

```
src/main/java/com/worldline/mock/
├── ConsumerReturnAuthApplication.java    # Main entry point
├── config/
│   ├── KafkaConfig.java                  # Topic creation, consumer factory
│   └── CryptoConfig.java                 # AES SecretKey bean
├── dto/
│   ├── BatchRequestMessage.java          # Incoming Kafka message
│   ├── BatchResponseMessage.java         # Outgoing Kafka message
│   └── TransactionCsvRow.java            # CSV row mapping
├── entity/
│   ├── AccountStatus.java                # ACTIVE, BLOCKED, SUSPENDED
│   ├── AuthorizationResult.java          # APPROVED, DECLINED
│   ├── BatchJob.java                     # Batch tracking entity
│   ├── BatchStatus.java                  # RECEIVED, PROCESSING, COMPLETED, FAILED
│   └── TransactionRecord.java           # Per-transaction decision entity
├── kafka/
│   └── BatchRequestConsumer.java         # Kafka listener + producer
├── repository/
│   ├── BatchJobRepository.java
│   └── TransactionRecordRepository.java
└── service/
    ├── AuthorizationEngine.java          # Rule-based mock decisions
    ├── BatchProcessingService.java       # Orchestrator
    └── CsvDecryptionService.java         # AES decryption
```
