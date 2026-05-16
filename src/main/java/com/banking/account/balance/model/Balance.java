package com.banking.account.balance.model;

import com.banking.account.bankAccount.model.BankAccount;
import com.banking.account.exception.CurrencyMismatchException;
import com.banking.account.exception.InsufficientFundsException;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Holds the live financial balance of a {@link BankAccount}.
 *
 * <p><strong>Why a separate entity?</strong> Separating balance from the account
 * root gives Hibernate Envers a dedicated {@code balances_aud} table that records
 * every credit, debit, and block operation with its own revision. This makes
 * balance history queries efficient and keeps the {@code bank_accounts_aud} table
 * lean (structural changes only).</p>
 *
 * <p><strong>Joda Money strategy:</strong> Amounts are persisted as plain
 * {@code DECIMAL(19,4)} columns + a {@code currency_code CHAR(3)} column.
 * All Joda {@link Money} arithmetic is performed in-memory via the
 * {@link #getAvailableMoney()}, {@link #getBlockedMoney()}, etc. accessors.
 * The mutating domain methods ({@link #credit}, {@link #debit}, etc.) accept
 * {@link Money} parameters for full type-safety at the call site.</p>
 *
 * <p><strong>Precision model:</strong>
 * {@code availableAmount + blockedAmount = totalAmount} always holds as an
 * invariant after each operation. Blocking moves funds from available to blocked;
 * unblocking reverses this. Crediting increases available; debiting decreases it.</p>
 */
@Entity
@Audited
@Table(
        name = "balances",
        indexes = {
                @Index(name = "idx_balance_account_id", columnList = "account_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "bankAccount")
public class Balance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "BINARY(16)")
    @EqualsAndHashCode.Include
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private BankAccount bankAccount;

    // ─── Persisted raw values ────────────────────────────────────────────

    /**
     * Funds immediately available for withdrawal, transfer, or card payment.
     * DECIMAL(19,4) — 4 decimal places covers all ISO 4217 currencies.
     */
    @Column(name = "available_amount", precision = 19, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal availableAmount = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    /**
     * Funds reserved (e.g., a pending card pre-authorisation) — no longer
     * available but not yet fully debited.
     */
    @Column(name = "blocked_amount", precision = 19, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal blockedAmount = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    /**
     * ISO 4217 currency code. Every amount in this balance shares one currency —
     * multi-currency is handled at the account level (one account per currency).
     */
    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;


    // ─── Joda Money view accessors (@Transient — not persisted) ─────────

    /**
     * Available funds as a Joda {@link Money} instance.
     */
    @Transient
    public Money getAvailableMoney() {
        return Money.of(CurrencyUnit.of(currencyCode),
                availableAmount, RoundingMode.HALF_UP);
    }

    /**
     * Blocked (reserved) funds as a Joda {@link Money} instance.
     */
    @Transient
    public Money getBlockedMoney() {
        return Money.of(CurrencyUnit.of(currencyCode),
                blockedAmount, RoundingMode.HALF_UP);
    }

    /**
     * Total ledger balance (available + blocked) as a Joda {@link Money} instance.
     */
    @Transient
    public Money getTotalMoney() {
        return getAvailableMoney().plus(getBlockedMoney());
    }

    /**
     * Currency unit derived from the stored ISO code.
     */
    @Transient
    public CurrencyUnit getCurrencyUnit() {
        return CurrencyUnit.of(currencyCode);
    }

    // ─── Domain mutating operations ──────────────────────────────────────

    /**
     * Credits the account — increases available balance.
     * Used for incoming transfers, salary deposits, interest, etc.
     *
     * @param amount the amount to credit; must match account currency
     * @throws CurrencyMismatchException if currency differs from account currency
     */
    public void credit(Money amount) {
        assertValidAmount(amount);
        this.availableAmount = this.availableAmount
                .add(amount.getAmount())
                .setScale(4, RoundingMode.HALF_UP);
        touch();
    }

    /**
     * Debits the account — decreases available balance.
     * Used for outgoing transfers, fee deductions, etc.
     *
     * @param amount the amount to debit; must match account currency
     * @throws InsufficientFundsException if available balance is less than requested
     * @throws CurrencyMismatchException  if currency differs from account currency
     */
    public void debit(Money amount) {
        assertValidAmount(amount);
        if (getAvailableMoney().isLessThan(amount)) {
            throw new InsufficientFundsException(
                    "Insufficient funds. Available: %s, Requested: %s"
                            .formatted(getAvailableMoney(), amount));
        }
        this.availableAmount = this.availableAmount
                .subtract(amount.getAmount())
                .setScale(4, RoundingMode.HALF_UP);
        touch();
    }

    /**
     * Moves funds from available to blocked (e.g. card pre-authorisation).
     *
     * @param amount the amount to block; must match account currency
     * @throws InsufficientFundsException if available balance is insufficient
     */
    public void block(Money amount) {
        assertValidAmount(amount);
        if (getAvailableMoney().isLessThan(amount)) {
            throw new InsufficientFundsException(
                    "Insufficient available funds to block. Available: %s, Requested: %s"
                            .formatted(getAvailableMoney(), amount));
        }
        this.availableAmount = this.availableAmount
                .subtract(amount.getAmount())
                .setScale(4, RoundingMode.HALF_UP);
        this.blockedAmount = this.blockedAmount
                .add(amount.getAmount())
                .setScale(4, RoundingMode.HALF_UP);
        touch();
    }

    /**
     * Moves funds from blocked back to available (e.g. pre-authorisation released).
     *
     * @param amount the amount to unblock; must be ≤ blocked balance
     */
    public void unblock(Money amount) {
        assertValidAmount(amount);
        if (getBlockedMoney().isLessThan(amount)) {
            throw new IllegalStateException(
                    "Cannot unblock more than is blocked. Blocked: %s, Requested: %s"
                            .formatted(getBlockedMoney(), amount));
        }
        this.blockedAmount = this.blockedAmount
                .subtract(amount.getAmount())
                .setScale(4, RoundingMode.HALF_UP);
        this.availableAmount = this.availableAmount
                .add(amount.getAmount())
                .setScale(4, RoundingMode.HALF_UP);
        touch();
    }

    /**
     * Settles a previously blocked amount — deducts from blocked (not available).
     * Used when a card pre-auth is captured by the merchant.
     *
     * @param amount the amount to settle; must be ≤ blocked balance
     */
    public void settleBlocked(Money amount) {
        assertValidAmount(amount);
        if (getBlockedMoney().isLessThan(amount)) {
            throw new IllegalStateException(
                    "Cannot settle more than is blocked. Blocked: %s, Requested: %s"
                            .formatted(getBlockedMoney(), amount));
        }
        this.blockedAmount = this.blockedAmount
                .subtract(amount.getAmount())
                .setScale(4, RoundingMode.HALF_UP);
        touch();
    }

    // ─── Factory ─────────────────────────────────────────────────────────

    /**
     * Creates a zero-balance instance for a newly opened account.
     */
    public static Balance zeroBalance(BankAccount account, String currencyCode) {
        return Balance.builder()
                .bankAccount(account)
                .availableAmount(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP))
                .blockedAmount(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP))
                .currencyCode(currencyCode)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    // ─── Invariant checks ────────────────────────────────────────────────

    private void assertValidAmount(Money amount) {
        if (amount == null) throw new IllegalArgumentException("Amount must not be null");
        if (amount.isNegative() || amount.isZero()) {
            throw new IllegalArgumentException("Amount must be positive: " + amount);
        }
        if (!amount.getCurrencyUnit().getCode().equals(this.currencyCode)) {
            throw new CurrencyMismatchException(
                    "Currency mismatch. Account currency: %s, Provided: %s"
                            .formatted(this.currencyCode, amount.getCurrencyUnit().getCode()));
        }
    }

    private void touch() {
        this.lastUpdated = LocalDateTime.now();
    }
}
