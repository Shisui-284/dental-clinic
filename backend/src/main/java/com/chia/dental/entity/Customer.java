package com.chia.dental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "customer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "phone", nullable = false, unique = true, length = 15)
    private String phone;

    @Column(name = "is_blacklisted", nullable = false)
    private Boolean isBlacklisted;

    @Column(name = "first_consent_at")
    private OffsetDateTime firstConsentAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}

