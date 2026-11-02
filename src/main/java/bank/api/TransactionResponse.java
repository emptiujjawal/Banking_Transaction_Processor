package bank.api;

import bank.EntryType;
import bank.LedgerEntry;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        String accountId,
        EntryType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Instant timestamp,
        String counterpartyAccountId,
        String transferId) {

    static TransactionResponse from(LedgerEntry entry) {
        return new TransactionResponse(
                entry.accountId(),
                entry.type(),
                entry.amount().toBigDecimal(),
                entry.balanceAfter().toBigDecimal(),
                entry.timestamp(),
                entry.counterpartyAccountId(),
                entry.transferId());
    }
}
