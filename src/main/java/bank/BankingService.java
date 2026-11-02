package bank;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry point for opening accounts, moving money and querying the results.
 * Everything is kept in memory.
 */
public class BankingService {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final Clock clock;

    public BankingService() {
        this(Clock.systemUTC());
    }

    public BankingService(Clock clock) {
        this.clock = clock;
    }

    public void openAccount(String accountId) {
        openAccount(accountId, Money.ZERO);
    }

    public void openAccount(String accountId, Money openingBalance) {
        if (openingBalance == null) {
            throw new InvalidAmountException("Opening balance is required");
        }
        Account account = new Account(accountId, openingBalance, clock);
        if (accounts.putIfAbsent(accountId, account) != null) {
            throw new DuplicateAccountException(accountId);
        }
    }

    /** Returns the ledger entry that was written. */
    public LedgerEntry deposit(String accountId, Money amount) {
        requirePositive(amount);
        return find(accountId).credit(EntryType.DEPOSIT, amount, null, null);
    }

    /** Returns the ledger entry that was written. */
    public LedgerEntry withdraw(String accountId, Money amount) {
        requirePositive(amount);
        return find(accountId).debit(EntryType.WITHDRAWAL, amount, null, null);
    }

    /** Returns the source account's side of the transfer. */
    public LedgerEntry transfer(String fromAccountId, String toAccountId, Money amount) {
        requirePositive(amount);
        if (fromAccountId != null && fromAccountId.equals(toAccountId)) {
            throw new InvalidTransferException("Cannot transfer to the same account: " + fromAccountId);
        }
        // Look both accounts up before touching either, so a bad destination
        // can never leave the source already debited.
        Account from = find(fromAccountId);
        Account to = find(toAccountId);

        // Always lock in id order. Two opposite transfers (A->B and B->A) would
        // otherwise each hold one lock and wait on the other forever.
        Account first = fromAccountId.compareTo(toAccountId) < 0 ? from : to;
        Account second = first == from ? to : from;

        LedgerEntry sent;
        first.lock();
        try {
            second.lock();
            try {
                String transferId = UUID.randomUUID().toString();
                // The debit is the only step that can fail, and it runs first.
                // Once it passes, the credit cannot fail, so no rollback is needed.
                sent = from.debit(EntryType.TRANSFER_OUT, amount, toAccountId, transferId);
                to.credit(EntryType.TRANSFER_IN, amount, fromAccountId, transferId);
            } finally {
                second.unlock();
            }
        } finally {
            first.unlock();
        }
        return sent;
    }

    public Money getBalance(String accountId) {
        return find(accountId).balance();
    }

    /** Oldest entry first. The returned list is an immutable snapshot. */
    public List<LedgerEntry> getTransactionHistory(String accountId) {
        return find(accountId).ledger();
    }

    private Account find(String accountId) {
        Account account = accountId == null ? null : accounts.get(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }
        return account;
    }

    private static void requirePositive(Money amount) {
        if (amount == null) {
            throw new InvalidAmountException("Amount is required");
        }
        if (amount.isZero()) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
    }
}
