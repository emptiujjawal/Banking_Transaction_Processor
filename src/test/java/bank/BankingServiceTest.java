package bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BankingServiceTest {

    private static final Instant START = Instant.parse("2026-01-01T09:00:00Z");

    private MutableClock clock;
    private BankingService bank;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        bank = new BankingService(clock);
    }

    private static Money money(String amount) {
        return Money.of(amount);
    }

    @Nested
    class OpeningAccounts {

        @Test
        void accountStartsWithTheGivenBalance() {
            bank.openAccount("ACC-1", money("100.00"));

            assertThat(bank.getBalance("ACC-1")).isEqualTo(money("100"));
        }

        @Test
        void accountStartsAtZeroWhenNoBalanceIsGiven() {
            bank.openAccount("ACC-1");

            assertThat(bank.getBalance("ACC-1")).isEqualTo(Money.ZERO);
        }

        @Test
        void accountIdsMustBeUnique() {
            bank.openAccount("ACC-1", money("100"));

            assertThatThrownBy(() -> bank.openAccount("ACC-1", money("5")))
                    .isInstanceOf(DuplicateAccountException.class);
            assertThat(bank.getBalance("ACC-1")).isEqualTo(money("100"));
        }

        @Test
        void accountIdsAreCaseSensitive() {
            bank.openAccount("acc-1");
            bank.openAccount("ACC-1");

            assertThat(bank.getBalance("acc-1")).isEqualTo(Money.ZERO);
        }

        @Test
        void blankOrMissingIdIsRejected() {
            assertThatThrownBy(() -> bank.openAccount(null)).isInstanceOf(InvalidAccountIdException.class);
            assertThatThrownBy(() -> bank.openAccount("")).isInstanceOf(InvalidAccountIdException.class);
            assertThatThrownBy(() -> bank.openAccount("   ")).isInstanceOf(InvalidAccountIdException.class);
        }

        @Test
        void missingOpeningBalanceIsRejected() {
            assertThatThrownBy(() -> bank.openAccount("ACC-1", null))
                    .isInstanceOf(InvalidAmountException.class);
        }

        @Test
        void aNonZeroOpeningBalanceAppearsInTheLedger() {
            bank.openAccount("ACC-1", money("100"));

            assertThat(bank.getTransactionHistory("ACC-1")).singleElement().satisfies(entry -> {
                assertThat(entry.type()).isEqualTo(EntryType.OPENING_BALANCE);
                assertThat(entry.amount()).isEqualTo(money("100"));
                assertThat(entry.balanceAfter()).isEqualTo(money("100"));
                assertThat(entry.timestamp()).isEqualTo(START);
            });
        }

        @Test
        void aZeroOpeningBalanceLeavesTheLedgerEmpty() {
            bank.openAccount("ACC-1");

            assertThat(bank.getTransactionHistory("ACC-1")).isEmpty();
        }
    }

    @Nested
    class Deposits {

        @BeforeEach
        void openAccount() {
            bank.openAccount("ACC-1", money("50"));
        }

        @Test
        void increasesTheBalance() {
            bank.deposit("ACC-1", money("25.50"));

            assertThat(bank.getBalance("ACC-1")).isEqualTo(money("75.50"));
        }

        @Test
        void isRecordedInTheLedgerWithItsTimestampAndResultingBalance() {
            clock.advance(Duration.ofMinutes(5));
            bank.deposit("ACC-1", money("25"));

            LedgerEntry entry = lastEntry("ACC-1");
            assertThat(entry.type()).isEqualTo(EntryType.DEPOSIT);
            assertThat(entry.accountId()).isEqualTo("ACC-1");
            assertThat(entry.amount()).isEqualTo(money("25"));
            assertThat(entry.balanceAfter()).isEqualTo(money("75"));
            assertThat(entry.timestamp()).isEqualTo(START.plus(Duration.ofMinutes(5)));
            assertThat(entry.counterpartyAccountId()).isNull();
            assertThat(entry.transferId()).isNull();
        }

        @Test
        void zeroIsRejected() {
            assertThatThrownBy(() -> bank.deposit("ACC-1", Money.ZERO))
                    .isInstanceOf(InvalidAmountException.class);
        }

        @Test
        void missingAmountIsRejected() {
            assertThatThrownBy(() -> bank.deposit("ACC-1", null))
                    .isInstanceOf(InvalidAmountException.class);
        }

        @Test
        void rejectedDepositLeavesNoTrace() {
            assertThatThrownBy(() -> bank.deposit("ACC-1", Money.ZERO));

            assertThat(bank.getBalance("ACC-1")).isEqualTo(money("50"));
            assertThat(bank.getTransactionHistory("ACC-1")).hasSize(1);
        }

        @Test
        void unknownAccountIsRejected() {
            assertThatThrownBy(() -> bank.deposit("NOPE", money("10")))
                    .isInstanceOf(AccountNotFoundException.class);
        }

        @Test
        void missingAccountIdIsTreatedAsUnknownAccount() {
            assertThatThrownBy(() -> bank.deposit(null, money("10")))
                    .isInstanceOf(AccountNotFoundException.class);
        }
    }

    @Nested
    class Withdrawals {

        @BeforeEach
        void openAccount() {
            bank.openAccount("ACC-1", money("100"));
        }

        @Test
        void decreasesTheBalance() {
            bank.withdraw("ACC-1", money("30.25"));

            assertThat(bank.getBalance("ACC-1")).isEqualTo(money("69.75"));
        }

        @Test
        void isRecordedInTheLedger() {
            bank.withdraw("ACC-1", money("30"));

            LedgerEntry entry = lastEntry("ACC-1");
            assertThat(entry.type()).isEqualTo(EntryType.WITHDRAWAL);
            assertThat(entry.amount()).isEqualTo(money("30"));
            assertThat(entry.balanceAfter()).isEqualTo(money("70"));
        }

        @Test
        void theWholeBalanceCanBeWithdrawn() {
            bank.withdraw("ACC-1", money("100"));

            assertThat(bank.getBalance("ACC-1")).isEqualTo(Money.ZERO);
        }

        @Test
        void overdraftByOnePennyIsRejected() {
            assertThatThrownBy(() -> bank.withdraw("ACC-1", money("100.01")))
                    .isInstanceOf(InsufficientFundsException.class);
        }

        @Test
        void rejectedOverdraftLeavesBalanceAndLedgerUntouched() {
            assertThatThrownBy(() -> bank.withdraw("ACC-1", money("500")));

            assertThat(bank.getBalance("ACC-1")).isEqualTo(money("100"));
            assertThat(bank.getTransactionHistory("ACC-1")).hasSize(1);
        }

        @Test
        void accountWithNothingInItCannotBeWithdrawnFrom() {
            bank.openAccount("EMPTY");

            assertThatThrownBy(() -> bank.withdraw("EMPTY", money("0.01")))
                    .isInstanceOf(InsufficientFundsException.class);
        }

        @Test
        void zeroIsRejected() {
            assertThatThrownBy(() -> bank.withdraw("ACC-1", Money.ZERO))
                    .isInstanceOf(InvalidAmountException.class);
        }

        @Test
        void unknownAccountIsRejected() {
            assertThatThrownBy(() -> bank.withdraw("NOPE", money("10")))
                    .isInstanceOf(AccountNotFoundException.class);
        }
    }

    @Nested
    class Transfers {

        @BeforeEach
        void openAccounts() {
            bank.openAccount("A", money("100"));
            bank.openAccount("B", money("20"));
        }

        @Test
        void movesMoneyFromOneAccountToTheOther() {
            bank.transfer("A", "B", money("40"));

            assertThat(bank.getBalance("A")).isEqualTo(money("60"));
            assertThat(bank.getBalance("B")).isEqualTo(money("60"));
        }

        @Test
        void bothAccountsGetALedgerEntryPointingAtTheOtherSide() {
            bank.transfer("A", "B", money("40"));

            LedgerEntry out = lastEntry("A");
            LedgerEntry in = lastEntry("B");
            assertThat(out.type()).isEqualTo(EntryType.TRANSFER_OUT);
            assertThat(out.counterpartyAccountId()).isEqualTo("B");
            assertThat(out.balanceAfter()).isEqualTo(money("60"));
            assertThat(in.type()).isEqualTo(EntryType.TRANSFER_IN);
            assertThat(in.counterpartyAccountId()).isEqualTo("A");
            assertThat(in.balanceAfter()).isEqualTo(money("60"));
        }

        @Test
        void bothLegsShareATransferIdAndTimestamp() {
            bank.transfer("A", "B", money("40"));

            LedgerEntry out = lastEntry("A");
            LedgerEntry in = lastEntry("B");
            assertThat(out.transferId()).isNotNull().isEqualTo(in.transferId());
            assertThat(out.timestamp()).isEqualTo(in.timestamp());
        }

        @Test
        void eachTransferGetsItsOwnId() {
            bank.transfer("A", "B", money("1"));
            bank.transfer("A", "B", money("1"));

            List<LedgerEntry> history = bank.getTransactionHistory("A");
            String first = history.get(1).transferId();
            String second = history.get(2).transferId();
            assertThat(first).isNotEqualTo(second);
        }

        @Test
        void theWholeBalanceCanBeTransferred() {
            bank.transfer("A", "B", money("100"));

            assertThat(bank.getBalance("A")).isEqualTo(Money.ZERO);
            assertThat(bank.getBalance("B")).isEqualTo(money("120"));
        }

        @Test
        void insufficientFundsChangesNothingOnEitherSide() {
            assertThatThrownBy(() -> bank.transfer("A", "B", money("100.01")))
                    .isInstanceOf(InsufficientFundsException.class);

            assertThat(bank.getBalance("A")).isEqualTo(money("100"));
            assertThat(bank.getBalance("B")).isEqualTo(money("20"));
            assertThat(bank.getTransactionHistory("A")).hasSize(1);
            assertThat(bank.getTransactionHistory("B")).hasSize(1);
        }

        @Test
        void unknownDestinationDoesNotDebitTheSource() {
            assertThatThrownBy(() -> bank.transfer("A", "NOPE", money("10")))
                    .isInstanceOf(AccountNotFoundException.class);

            assertThat(bank.getBalance("A")).isEqualTo(money("100"));
            assertThat(bank.getTransactionHistory("A")).hasSize(1);
        }

        @Test
        void unknownSourceDoesNotCreditTheDestination() {
            assertThatThrownBy(() -> bank.transfer("NOPE", "B", money("10")))
                    .isInstanceOf(AccountNotFoundException.class);

            assertThat(bank.getBalance("B")).isEqualTo(money("20"));
            assertThat(bank.getTransactionHistory("B")).hasSize(1);
        }

        @Test
        void transferToTheSameAccountIsRejected() {
            assertThatThrownBy(() -> bank.transfer("A", "A", money("10")))
                    .isInstanceOf(InvalidTransferException.class);

            assertThat(bank.getBalance("A")).isEqualTo(money("100"));
            assertThat(bank.getTransactionHistory("A")).hasSize(1);
        }

        @Test
        void zeroAndMissingAmountsAreRejected() {
            assertThatThrownBy(() -> bank.transfer("A", "B", Money.ZERO))
                    .isInstanceOf(InvalidAmountException.class);
            assertThatThrownBy(() -> bank.transfer("A", "B", null))
                    .isInstanceOf(InvalidAmountException.class);
        }

        @Test
        void missingAccountIdsAreTreatedAsUnknownAccounts() {
            assertThatThrownBy(() -> bank.transfer(null, "B", money("1")))
                    .isInstanceOf(AccountNotFoundException.class);
            assertThatThrownBy(() -> bank.transfer("A", null, money("1")))
                    .isInstanceOf(AccountNotFoundException.class);
            assertThatThrownBy(() -> bank.transfer(null, null, money("1")))
                    .isInstanceOf(AccountNotFoundException.class);
        }

        @Test
        void totalMoneyInTheSystemIsUnchanged() {
            bank.transfer("A", "B", money("33.33"));
            bank.transfer("B", "A", money("0.01"));

            Money total = bank.getBalance("A").plus(bank.getBalance("B"));
            assertThat(total).isEqualTo(money("120"));
        }
    }

    @Nested
    class Queries {

        @Test
        void balanceOfAnUnknownAccountIsAnError() {
            assertThatThrownBy(() -> bank.getBalance("NOPE")).isInstanceOf(AccountNotFoundException.class);
        }

        @Test
        void historyOfAnUnknownAccountIsAnError() {
            assertThatThrownBy(() -> bank.getTransactionHistory("NOPE"))
                    .isInstanceOf(AccountNotFoundException.class);
        }

        @Test
        void historyIsOldestFirst() {
            bank.openAccount("ACC-1", money("100"));
            clock.advance(Duration.ofSeconds(1));
            bank.deposit("ACC-1", money("10"));
            clock.advance(Duration.ofSeconds(1));
            bank.withdraw("ACC-1", money("5"));

            assertThat(bank.getTransactionHistory("ACC-1"))
                    .extracting(LedgerEntry::type)
                    .containsExactly(EntryType.OPENING_BALANCE, EntryType.DEPOSIT, EntryType.WITHDRAWAL);
            assertThat(bank.getTransactionHistory("ACC-1"))
                    .extracting(LedgerEntry::timestamp)
                    .isSorted();
        }

        @Test
        void historyOnlyContainsTheAccountsOwnEntries() {
            bank.openAccount("A", money("100"));
            bank.openAccount("B", money("100"));
            bank.deposit("B", money("1"));

            assertThat(bank.getTransactionHistory("A")).hasSize(1);
        }

        @Test
        void lastEntryBalanceAlwaysMatchesTheCurrentBalance() {
            bank.openAccount("A", money("100"));
            bank.openAccount("B");
            bank.deposit("A", money("12.34"));
            bank.transfer("A", "B", money("50"));
            bank.withdraw("A", money("1.10"));

            assertThat(lastEntry("A").balanceAfter()).isEqualTo(bank.getBalance("A"));
            assertThat(lastEntry("B").balanceAfter()).isEqualTo(bank.getBalance("B"));
        }

        @Test
        void returnedHistoryCannotBeModified() {
            bank.openAccount("ACC-1", money("100"));
            List<LedgerEntry> history = bank.getTransactionHistory("ACC-1");

            assertThatThrownBy(history::clear).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void returnedHistoryIsASnapshotAndDoesNotGrowLater() {
            bank.openAccount("ACC-1", money("100"));
            List<LedgerEntry> before = bank.getTransactionHistory("ACC-1");

            bank.deposit("ACC-1", money("1"));

            assertThat(before).hasSize(1);
            assertThat(bank.getTransactionHistory("ACC-1")).hasSize(2);
        }
    }

    private LedgerEntry lastEntry(String accountId) {
        List<LedgerEntry> history = bank.getTransactionHistory(accountId);
        return history.get(history.size() - 1);
    }
}
