package bank;

import java.math.BigDecimal;

/**
 * A non-negative amount of money with exactly two decimal places.
 *
 * Values with more than two significant decimals (e.g. 10.005) are rejected
 * rather than rounded, so a caller never gets a silently different amount.
 * A single currency is assumed throughout.
 */
public final class Money implements Comparable<Money> {

    public static final Money ZERO = new Money(BigDecimal.ZERO.setScale(2));

    /** Upper bound for a single amount; stops absurd inputs like 1E+999999999. */
    public static final Money MAX_AMOUNT = new Money(new BigDecimal("999999999999.99"));

    private final BigDecimal value;

    private Money(BigDecimal value) {
        this.value = value;
    }

    public static Money of(String text) {
        if (text == null) {
            throw new InvalidAmountException("Amount is required");
        }
        try {
            return of(new BigDecimal(text.trim()));
        } catch (NumberFormatException e) {
            throw new InvalidAmountException("Not a valid amount: '" + text + "'");
        }
    }

    public static Money of(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidAmountException("Amount is required");
        }
        if (amount.signum() < 0) {
            throw new InvalidAmountException("Amount cannot be negative: " + amount.toPlainString());
        }
        if (amount.compareTo(MAX_AMOUNT.value) > 0) {
            throw new InvalidAmountException("Amount exceeds the maximum of " + MAX_AMOUNT);
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidAmountException("Amount has more than 2 decimal places: " + amount.toPlainString());
        }
        return new Money(amount.setScale(2));
    }

    public Money plus(Money other) {
        return new Money(value.add(other.value));
    }

    /** Callers must make sure this is not smaller than {@code other}. */
    public Money minus(Money other) {
        if (isLessThan(other)) {
            throw new IllegalArgumentException(this + " is less than " + other);
        }
        return new Money(value.subtract(other.value));
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    public boolean isLessThan(Money other) {
        return value.compareTo(other.value) < 0;
    }

    public BigDecimal toBigDecimal() {
        return value;
    }

    @Override
    public int compareTo(Money other) {
        return value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Money other && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
