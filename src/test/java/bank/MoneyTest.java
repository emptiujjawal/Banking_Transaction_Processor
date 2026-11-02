package bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void amountsAreStoredWithTwoDecimalPlaces() {
        assertThat(Money.of("10")).hasToString("10.00");
        assertThat(Money.of("10.5")).hasToString("10.50");
    }

    @Test
    void sameValueIsEqualRegardlessOfHowItWasWritten() {
        assertThat(Money.of("10")).isEqualTo(Money.of("10.00"));
        assertThat(Money.of("10.0")).isEqualTo(Money.of(new BigDecimal("10.000")));
        assertThat(Money.of("10")).hasSameHashCodeAs(Money.of("10.00"));
    }

    @Test
    void trailingZerosBeyondTwoDecimalsAreHarmless() {
        assertThat(Money.of("1.500")).isEqualTo(Money.of("1.50"));
    }

    @Test
    void zeroIsAllowedAsAValue() {
        assertThat(Money.of("0").isZero()).isTrue();
        assertThat(Money.of("0.00")).isEqualTo(Money.ZERO);
    }

    @Test
    void negativeAmountsAreRejected() {
        assertThatThrownBy(() -> Money.of("-0.01")).isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void amountsAreNeverSilentlyRounded() {
        assertThatThrownBy(() -> Money.of("10.005")).isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void missingAmountIsRejected() {
        assertThatThrownBy(() -> Money.of((String) null)).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> Money.of((BigDecimal) null)).isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void nonNumericTextIsRejected() {
        assertThatThrownBy(() -> Money.of("abc")).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> Money.of("")).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> Money.of("NaN")).isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void amountsAboveTheMaximumAreRejected() {
        assertThat(Money.of("999999999999.99")).isEqualTo(Money.MAX_AMOUNT);
        assertThatThrownBy(() -> Money.of("1000000000000")).isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void hugeExponentsAreRejectedWithoutBlowingUp() {
        assertThatThrownBy(() -> Money.of("1E+999999999")).isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void additionAndSubtraction() {
        assertThat(Money.of("0.10").plus(Money.of("0.20"))).isEqualTo(Money.of("0.30"));
        assertThat(Money.of("5.00").minus(Money.of("1.25"))).isEqualTo(Money.of("3.75"));
    }

    @Test
    void subtractingMoreThanYouHaveIsAProgrammingError() {
        assertThatThrownBy(() -> Money.of("1").minus(Money.of("2")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparison() {
        assertThat(Money.of("1.00").isLessThan(Money.of("1.01"))).isTrue();
        assertThat(Money.of("1.00").isLessThan(Money.of("1"))).isFalse();
        assertThat(Money.of("2")).isGreaterThan(Money.of("1.99"));
    }
}
