package bank;

/** Base type for every error the processor raises on purpose. */
public class BankingException extends RuntimeException {

    public BankingException(String message) {
        super(message);
    }
}
