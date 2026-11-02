package bank.api;

import bank.Money;
import java.math.BigDecimal;

public record BalanceResponse(String accountId, BigDecimal balance) {

    BalanceResponse(String accountId, Money balance) {
        this(accountId, balance.toBigDecimal());
    }
}
