package bank.api;

import bank.BankingService;
import bank.LedgerEntry;
import bank.Money;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin HTTP layer over BankingService. It only translates JSON to domain
 * types and back; every rule lives in the service. Errors are turned into
 * HTTP statuses by ApiExceptionHandler.
 */
@RestController
public class AccountController {

    private final BankingService bank;

    public AccountController(BankingService bank) {
        this.bank = bank;
    }

    @PostMapping("/accounts")
    public ResponseEntity<BalanceResponse> openAccount(@RequestBody OpenAccountRequest request) {
        Money opening = request.openingBalance() == null ? Money.ZERO : Money.of(request.openingBalance());
        bank.openAccount(request.accountId(), opening);
        return ResponseEntity
                .created(URI.create("/accounts/" + request.accountId() + "/balance"))
                .body(new BalanceResponse(request.accountId(), bank.getBalance(request.accountId())));
    }

    @GetMapping("/accounts/{accountId}/balance")
    public BalanceResponse getBalance(@PathVariable String accountId) {
        return new BalanceResponse(accountId, bank.getBalance(accountId));
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public List<TransactionResponse> getTransactions(@PathVariable String accountId) {
        return bank.getTransactionHistory(accountId).stream()
                .map(TransactionResponse::from)
                .toList();
    }

    @PostMapping("/accounts/{accountId}/deposits")
    public ResponseEntity<TransactionResponse> deposit(
            @PathVariable String accountId, @RequestBody AmountRequest request) {
        LedgerEntry entry = bank.deposit(accountId, Money.of(request.amount()));
        return ResponseEntity.status(201).body(TransactionResponse.from(entry));
    }

    @PostMapping("/accounts/{accountId}/withdrawals")
    public ResponseEntity<TransactionResponse> withdraw(
            @PathVariable String accountId, @RequestBody AmountRequest request) {
        LedgerEntry entry = bank.withdraw(accountId, Money.of(request.amount()));
        return ResponseEntity.status(201).body(TransactionResponse.from(entry));
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransactionResponse> transfer(@RequestBody TransferRequest request) {
        LedgerEntry entry = bank.transfer(
                request.fromAccountId(), request.toAccountId(), Money.of(request.amount()));
        return ResponseEntity.status(201).body(TransactionResponse.from(entry));
    }

    public record OpenAccountRequest(String accountId, BigDecimal openingBalance) {
    }

    public record AmountRequest(BigDecimal amount) {
    }

    public record TransferRequest(String fromAccountId, String toAccountId, BigDecimal amount) {
    }
}
