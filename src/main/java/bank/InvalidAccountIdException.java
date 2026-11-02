package bank;

public class InvalidAccountIdException extends BankingException {

    public InvalidAccountIdException(String message) {
        super(message);
    }
}
