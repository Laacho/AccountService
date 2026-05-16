package com.banking.account.util;

import com.banking.account.exception.CurrencyMismatchException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Embeddable bridge between Joda Money and the relational database.
 *
 * <p>Hibernate 6+ does not support Jadira UserType, so we store money as two columns:
 * {@code amount} (DECIMAL 19,4) and {@code currency_code} (CHAR 3, ISO 4217).
 * The {@link #toMoney()} method reconstructs the Joda {@link Money} instance for
 * use in business logic.</p>
 *
 * <p>Usage in an entity (with AttributeOverrides to disambiguate column names
 * when the same entity embeds multiple MonetaryAmount fields):</p>
 * <pre>{@code
 * @Embedded
 * @AttributeOverrides({
 *     @AttributeOverride(name = "amount",
 *             column = @Column(name = "opening_balance_amount")),
 *     @AttributeOverride(name = "currencyCode",
 *             column = @Column(name = "opening_balance_currency"))
 * })
 * private MonetaryAmount openingBalance;
 * }</pre>
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA requires no-arg
public class MonetaryAmount {

    /**
     * The numeric amount, stored with 4 decimal places (DECIMAL 19,4).
     * Using 19 digits covers values up to ~9.9 quadrillion — sufficient for
     * any realistic banking scenario in any currency.
     */
    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    /**
     * ISO 4217 currency code (e.g. "BGN", "EUR", "USD").
     * Stored as CHAR(3) — always exactly 3 characters per the standard.
     */
    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    // ─── Static factories ───────────────────────────────────────────

    public MonetaryAmount(BigDecimal amount, String currencyCode) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        this.amount = amount.setScale(4, RoundingMode.HALF_UP);
        this.currencyCode = currencyCode;
    }

    /**
     * Constructs a {@code MonetaryAmount} from a Joda {@link Money} instance.
     */
    public static MonetaryAmount of(Money money) {
        Objects.requireNonNull(money, "money must not be null");
        return new MonetaryAmount(money.getAmount(), money.getCurrencyUnit().getCode());
    }

    /**
     * Constructs a zero {@code MonetaryAmount} for the given ISO currency code.
     */
    public static MonetaryAmount zero(String currencyCode) {
        return new MonetaryAmount(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP), currencyCode);
    }

    // ─── Joda Money conversion ──────────────────────────────────────

    /**
     * Reconstructs the Joda {@link Money} instance. Use this in service/domain
     * logic to benefit from Joda Money's type safety and arithmetic operations.
     */
    public Money toMoney() {
        return Money.of(CurrencyUnit.of(currencyCode), amount, RoundingMode.HALF_UP);
    }

    /** Returns the {@link CurrencyUnit} for this monetary amount. */
    public CurrencyUnit getCurrencyUnit() {
        return CurrencyUnit.of(currencyCode);
    }

    // ─── Arithmetic (returns new instances — immutable semantics) ───

    public MonetaryAmount plus(MonetaryAmount other) {
        assertSameCurrency(other);
        return new MonetaryAmount(this.amount.add(other.amount), this.currencyCode);
    }

    public MonetaryAmount minus(MonetaryAmount other) {
        assertSameCurrency(other);
        return new MonetaryAmount(this.amount.subtract(other.amount), this.currencyCode);
    }

    public MonetaryAmount plus(Money money) {
        return plus(MonetaryAmount.of(money));
    }

    public MonetaryAmount minus(Money money) {
        return minus(MonetaryAmount.of(money));
    }

    // ─── Comparisons ────────────────────────────────────────────────

    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isGreaterThan(MonetaryAmount other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isGreaterThanOrEqualTo(MonetaryAmount other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) >= 0;
    }

    public boolean isLessThan(MonetaryAmount other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }

    // ─── Validation ─────────────────────────────────────────────────

    private void assertSameCurrency(MonetaryAmount other) {
        if (!this.currencyCode.equals(other.currencyCode)) {
            throw new CurrencyMismatchException(
                    "Cannot operate on different currencies: %s vs %s"
                            .formatted(this.currencyCode, other.currencyCode));
        }
    }

    // ─── Object identity ────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MonetaryAmount that)) return false;
        return amount.compareTo(that.amount) == 0
                && Objects.equals(currencyCode, that.currencyCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currencyCode);
    }

    @Override
    public String toString() {
        return currencyCode + " " + amount.toPlainString();
    }
}
