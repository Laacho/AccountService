package com.banking.account.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.RevisionEntity;

/**
 * Custom Hibernate Envers revision entity.
 *
 * <p>Extends {@link DefaultRevisionEntity} which provides {@code id} (INT, auto-increment)
 * and {@code timestamp} (BIGINT, epoch millis). We add two extra columns:
 * <ul>
 *   <li>{@code modified_by} — the userId extracted from the JWT by
 *       {@link CustomRevisionListener}, giving us a "who changed this" audit trail.</li>
 *   <li>{@code client_ip}   — the remote IP address of the HTTP request.</li>
 * </ul>
 *
 * <p>Envers automatically creates a {@code revisions} table from this entity and
 * stores one row per revision (i.e. per @Transactional boundary that touches an
 * {@code @Audited} entity). All {@code *_aud} shadow tables reference this table
 * via {@code rev} FK.</p>
 *
 * <p>The resulting audit record for a balance debit looks like:</p>
 * <pre>
 *  revisions table:
 *    id=1001  timestamp=...  modified_by="uuid-of-user"  client_ip="10.0.1.5"
 *
 *  balances_aud table:
 *    id=<uuid>  rev=1001  revtype=1(MOD)  available_amount=750.0000  ...
 * </pre>
 */
@Entity
@RevisionEntity(CustomRevisionListener.class)
@Table(name = "revisions")
@Getter
@Setter
public class CustomRevisionEntity extends DefaultRevisionEntity {

    /**
     * UUID of the authenticated user who triggered this change.
     * Populated by {@link CustomRevisionListener} from Spring Security context.
     * {@code NULL} for background jobs (e.g. scheduled transaction processor).
     */
    @Column(name = "modified_by", length = 36)
    private String modifiedBy;

    /**
     * Remote IP address of the HTTP request that triggered this change.
     * IPv4 (15 chars) or IPv6 (up to 45 chars).
     */
    @Column(name = "client_ip", length = 45)
    private String clientIp;
}
