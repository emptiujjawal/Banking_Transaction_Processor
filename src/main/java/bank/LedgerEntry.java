package bank;

import java.time.Instant;

/**
 * One line of an account's ledger.
 *
 * counterpartyAccountId and transferId are only set for transfer entries; the
 * two legs of a transfer share the same transferId (both are null otherwise).
 */
public record LedgerEntry(
        String accountId,
        EntryType type,
        Money amount,
        Money balanceAfter,
        Instant timestamp,
        String counterpartyAccountId,
        String transferId) {
}
