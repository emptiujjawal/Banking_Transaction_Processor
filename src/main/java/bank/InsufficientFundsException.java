package bank;

public class InsufficientFundsException extends BankingException {

    public InsufficientFundsException(String accountId, Money balance, Money requested) {
        super("Account " + accountId + " has " + balance + " but " + requested + " was requested");
    }
}
