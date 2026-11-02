package bank;

public class InvalidTransferException extends BankingException {

    public InvalidTransferException(String message) {
        super(message);
    }
}
