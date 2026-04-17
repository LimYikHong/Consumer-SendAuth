package com.worldline.mock.dto;

import com.opencsv.bean.CsvBindByName;
import lombok.*;

/**
 * Maps a single row from the decrypted CSV file. Column names must match the
 * CSV header produced by the Batch Service.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionCsvRow {

    @CsvBindByName(column = "transactionId")
    private String transactionId;

    @CsvBindByName(column = "accountNumber")
    private String accountNumber;

    @CsvBindByName(column = "accountStatus")
    private String accountStatus;

    @CsvBindByName(column = "amount")
    private String amount;

    @CsvBindByName(column = "currency")
    private String currency;

    @CsvBindByName(column = "merchantName")
    private String merchantName;

    @CsvBindByName(column = "merchantCategory")
    private String merchantCategory;
}
