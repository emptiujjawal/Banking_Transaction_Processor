package bank;

public class AccountNotFoundException extends BankingException {

    public AccountNotFoundException(String accountId) {
        super("No such account: " + accountId);
    }
}
