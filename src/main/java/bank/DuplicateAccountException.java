package bank;

public class DuplicateAccountException extends BankingException {

    public DuplicateAccountException(String accountId) {
        super("Account already exists: " + accountId);
    }
}
