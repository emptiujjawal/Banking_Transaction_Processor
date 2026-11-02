package bank;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class BankingServiceConcurrencyTest {

    private static final int THREADS = 16;

    private final BankingService bank = new BankingService();

    @Test
    void concurrentDepositsAreNotLost() throws Exception {
        bank.openAccount("ACC-1");

        runInParallel(THREADS, () -> {
            for (int i = 0; i < 500; i++) {
                bank.deposit("ACC-1", Money.of("1.00"));
            }
            return null;
        });

        assertThat(bank.getBalance("ACC-1")).isEqualTo(Money.of("8000"));
        assertThat(bank.getTransactionHistory("ACC-1")).hasSize(THREADS * 500);
    }

    @Test
    void concurrentWithdrawalsCanNeverOverdraw() throws Exception {
        bank.openAccount("ACC-1", Money.of("100"));
        AtomicInteger succeeded = new AtomicInteger();

        runInParallel(THREADS, () -> {
            for (int i = 0; i < 50; i++) {
                try {
                    bank.withdraw("ACC-1", Money.of("1"));
                    succeeded.incrementAndGet();
                } catch (InsufficientFundsException expected) {
                    // the account ran dry, which is the point of the test
                }
            }
            return null;
        });

        assertThat(succeeded).hasValue(100);
        assertThat(bank.getBalance("ACC-1")).isEqualTo(Money.ZERO);
    }

    @Test
    void transfersInOppositeDirectionsDoNotDeadlockAndConserveMoney() throws Exception {
        bank.openAccount("A", Money.of("1000"));
        bank.openAccount("B", Money.of("1000"));
        AtomicInteger worker = new AtomicInteger();

        runInParallel(THREADS, () -> {
            boolean aToB = worker.getAndIncrement() % 2 == 0;
            for (int i = 0; i < 500; i++) {
                try {
                    if (aToB) {
                        bank.transfer("A", "B", Money.of("1"));
                    } else {
                        bank.transfer("B", "A", Money.of("1"));
                    }
                } catch (InsufficientFundsException ignored) {
                    // fine, balances are bounded
                }
            }
            return null;
        });

        Money total = bank.getBalance("A").plus(bank.getBalance("B"));
        assertThat(total).isEqualTo(Money.of("2000"));
    }

    private void runInParallel(int threads, Callable<Void> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        for (Future<Void> future : futures) {
            future.get();
        }
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }
}
