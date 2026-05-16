package com.banking.account.accountStatement.model;

import com.banking.account.bankAccount.model.BankAccount;
import com.banking.account.util.MonetaryAmount;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A generated periodic account statement, capturing the opening and closing
 * balances (as {@link MonetaryAmount} embeddables) and a count of transactions
 * within the period.
 *
 * <p>Statements are append-only — once generated they are never modified,
 * which is why this entity only uses {@link CreationTimestamp} and no
 * {@code UpdateTimestamp}. Envers is applied for compliance audit purposes.</p>
 *
 * <p>The full transaction line-items are NOT stored here; they live in
 * Transaction-Service and are fetched via a Feign client when the statement
 * is generated or displayed.</p>
 */
@Entity
@Audited
@Table(
    name = "account_statements",
    indexes = {
        @Index(name = "idx_stmt_account_id",  columnList = "account_id"),
        @Index(name = "idx_stmt_period",       columnList = "account_id, period_start, period_end"),
        @Index(name = "idx_stmt_ref",          columnList = "statement_ref")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "bankAccount")
public class AccountStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private BankAccount bankAccount;

    /** Inclusive start date of the statement period. */
    @Column(name = "period_start", nullable = false, updatable = false)
    private LocalDate periodStart;

    /** Inclusive end date of the statement period. */
    @Column(name = "period_end", nullable = false, updatable = false)
    private LocalDate periodEnd;

    /**
     * Balance at the very start of the period (before any transactions on periodStart).
     * Uses {@link MonetaryAmount} as an embeddable to pair the BigDecimal amount with
     * its ISO 4217 currency code in two dedicated columns.
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount",
                column = @Column(name = "opening_balance_amount",
                                 precision = 19, scale = 4, nullable = false)),
        @AttributeOverride(name = "currencyCode",
                column = @Column(name = "opening_balance_currency",
                                 length = 3, nullable = false))
    })
    private MonetaryAmount openingBalance;

    /**
     * Balance at the end of the period (after all transactions on periodEnd).
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount",
                column = @Column(name = "closing_balance_amount",
                                 precision = 19, scale = 4, nullable = false)),
        @AttributeOverride(name = "currencyCode",
                column = @Column(name = "closing_balance_currency",
                                 length = 3, nullable = false))
    })
    private MonetaryAmount closingBalance;

    /**
     * Total number of transactions (credits + debits) in the period.
     * Cached here so the statement header can be rendered without fetching
     * the full transaction list from Transaction-Service.
     */
    @Column(name = "transaction_count", nullable = false)
    private Integer transactionCount;

    /**
     * Human-readable unique reference for this statement,
     * e.g. "STMT-BG80BNBG96611020345678-2025-06".
     * Used as a stable identifier in download links and emails.
     */
    @Column(name = "statement_ref", nullable = false, unique = true, length = 80)
    private String statementRef;

    @CreationTimestamp
    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;
}
