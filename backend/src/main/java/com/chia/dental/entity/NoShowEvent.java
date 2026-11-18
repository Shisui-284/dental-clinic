package com.chia.dental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "no_show_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoShowEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private PatientProfile patient;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "is_reversed", nullable = false)
    private Boolean isReversed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversed_by")
    private StaffAccount reversedBy;

    @Column(name = "reversed_at")
    private OffsetDateTime reversedAt;

    @Column(name = "reversed_reason", length = 255)
    private String reversedReason;
}

