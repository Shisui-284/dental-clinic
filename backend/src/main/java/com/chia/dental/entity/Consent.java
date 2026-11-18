package com.chia.dental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "consent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consent_id")
    private Long consentId;

    @Column(name = "phone", nullable = false, length = 15)
    private String phone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "consent_time", nullable = false)
    private OffsetDateTime consentTime;

    @Column(name = "channel", nullable = false, length = 30)
    private String channel;

    @Column(name = "action", nullable = false, length = 10)
    private String action;

    @Column(name = "policy_version", nullable = false, length = 20)
    private String policyVersion;

    @Column(name = "consented_by", length = 100)
    private String consentedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "for_patient_id")
    private PatientProfile forPatient;

    @Column(name = "relation", length = 30)
    private String relation;

    @Column(name = "is_guardian", nullable = false)
    private Boolean isGuardian;
}

