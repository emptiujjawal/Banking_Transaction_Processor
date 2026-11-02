package bank;

public class InvalidAmountException extends BankingException {

    public InvalidAmountException(String message) {
        super(message);
    }
}
