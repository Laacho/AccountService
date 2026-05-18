package com.banking.account.accountLedger.model;

import com.banking.account.bankAccount.model.BankAccount;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Audited
@Table(
        name = "account_ledger_entries",
        indexes = {
                @Index(name = "idx_ledger_account_id", columnList = "account_id"),
                @Index(name = "idx_ledger_account_created", columnList = "account_id, created_at"),
                @Index(name = "idx_ledger_idempotency", columnList = "idempotency_key"),
                @Index(name = "idx_ledger_reference", columnList = "reference")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ledger_idempotency_account",
                        columnNames = {"idempotency_key", "account_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "bankAccount")
public class AccountLedgerEntry {



        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        @Column(updatable = false,nullable = false)
        @EqualsAndHashCode.Include
        private UUID id;


        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "account_id", nullable = false, updatable = false)
        private BankAccount bankAccount;

        @Enumerated(EnumType.STRING)
        @Column(name = "direction", nullable = false, updatable = false, length = 10)
        private LedgerDirection direction;


        @Column(precision = 19,scale=4,nullable = false,updatable = false)
        private BigDecimal amount;

        @Column(name = "currency_code", length = 3, nullable = false, updatable = false)
        private String currencyCode;

        @Column(name = "balance_before", precision = 19, scale = 4, nullable = false, updatable = false)
        private BigDecimal balanceBefore;

        @Column(name = "balance_after", precision = 19, scale = 4, nullable = false, updatable = false)
        private BigDecimal balanceAfter;

        /**
         * The other account in this transfer (recipient on DEBIT entries,
         * sender on CREDIT entries). Nullable for entries with no counterparty
         * (interest, fees, manual adjustments).
         */

        @Column(name = "counterparty_account_id", updatable = false,columnDefinition = "BINARY(16)")
        private UUID counterpartyAccountId;

        /** Human-readable correlation string from Transaction-Service. */
        @Column(length = 50, nullable = false, updatable = false)
        private String reference;

        /** Idempotency key from Transaction-Service — duplicate detection key. */
        @Column(name = "idempotency_key", length = 64, nullable = false, updatable = false)
        private String idempotencyKey;

        private String description;

        @CreationTimestamp
        @Column(name = "created_at", updatable = false,nullable = false)
        private LocalDateTime createdAt;
}
