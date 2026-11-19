package com.chia.dental.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;

/** Từ chối nhận tin CSKH/quảng cáo (SRS 2.2.9, FR-29). */
@Entity
@Table(name = "opt_out")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptOut {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "opt_out_id")
    private Long optOutId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** ZALO_ZNS | SMS | ALL */
    @Column(name = "channel", nullable = false, length = 10)
    private String channel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "excluded_notif_types", nullable = false, columnDefinition = "jsonb")
    private List<String> excludedNotifTypes;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;
}
