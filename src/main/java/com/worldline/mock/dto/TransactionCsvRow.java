package com.worldline.mock.dto;

import com.opencsv.bean.CsvBindByName;
import lombok.*;

/**
 * Maps a single row from the decrypted CSV file. Column names match the
 * producer's BatchFileGenerationService output:
 * transaction_id,merchant_id,merchant_customer,masked_pan,amount_cents,currency,actual_billing_date,recurring_reference
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionCsvRow {

    @CsvBindByName(column = "transaction_id")
    private String transactionId;

    @CsvBindByName(column = "merchant_id")
    private String merchantId;

    @CsvBindByName(column = "merchant_customer")
    private String merchantCustomer;

    @CsvBindByName(column = "masked_pan")
    private String maskedPan;

    @CsvBindByName(column = "amount_cents")
    private String amountCents;

    @CsvBindByName(column = "currency")
    private String currency;

    @CsvBindByName(column = "actual_billing_date")
    private String actualBillingDate;

    @CsvBindByName(column = "recurring_reference")
    private String recurringReference;
}
