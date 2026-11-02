package bank;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An account and its ledger. Balance and ledger only change together, under
 * the account's lock, so the last ledger entry always matches the balance.
 *
 * The lock is exposed so BankingService can hold two accounts at once during a
 * transfer. It is reentrant, so the methods below can still lock on their own.
 */
final class Account {

    private final String id;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();
    private final List<LedgerEntry> ledger = new ArrayList<>();
    private Money balance = Money.ZERO;

    Account(String id, Money openingBalance, Clock clock) {
        if (id == null || id.isBlank()) {
            throw new InvalidAccountIdException("Account id must not be blank");
        }
        this.id = id;
        this.clock = clock;
        if (!openingBalance.isZero()) {
            credit(EntryType.OPENING_BALANCE, openingBalance, null, null);
        }
    }

    String id() {
        return id;
    }

    void lock() {
        lock.lock();
    }

    void unlock() {
        lock.unlock();
    }

    Money balance() {
        lock.lock();
        try {
            return balance;
        } finally {
            lock.unlock();
        }
    }

    List<LedgerEntry> ledger() {
        lock.lock();
        try {
            return List.copyOf(ledger);
        } finally {
            lock.unlock();
        }
    }

    LedgerEntry credit(EntryType type, Money amount, String counterparty, String transferId) {
        lock.lock();
        try {
            balance = balance.plus(amount);
            return record(type, amount, counterparty, transferId);
        } finally {
            lock.unlock();
        }
    }

    LedgerEntry debit(EntryType type, Money amount, String counterparty, String transferId) {
        lock.lock();
        try {
            if (balance.isLessThan(amount)) {
                throw new InsufficientFundsException(id, balance, amount);
            }
            balance = balance.minus(amount);
            return record(type, amount, counterparty, transferId);
        } finally {
            lock.unlock();
        }
    }

    private LedgerEntry record(EntryType type, Money amount, String counterparty, String transferId) {
        LedgerEntry entry = new LedgerEntry(id, type, amount, balance, clock.instant(), counterparty, transferId);
        ledger.add(entry);
        return entry;
    }
}
