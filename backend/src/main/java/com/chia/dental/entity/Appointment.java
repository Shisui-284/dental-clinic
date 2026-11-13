package com.chia.dental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "appointment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appointment_id")
    private Long appointmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private PatientProfile patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private StaffAccount staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chair_id", nullable = false)
    private DentalChair chair;

    @Column(name = "date_time", nullable = false)
    private OffsetDateTime dateTime;

    @Column(name = "duration", nullable = false)
    private Short duration;

    @Column(name = "end_time", insertable = false, updatable = false)
    private OffsetDateTime endTime;

    @Column(name = "status", nullable = false, length = 12)
    private String status;

    @Column(name = "source", nullable = false, length = 10)
    private String source;

    @Column(name = "booked_by_phone", length = 15)
    private String bookedByPhone;

    @Column(name = "hold_until")
    private OffsetDateTime holdUntil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private StaffAccount cancelledBy;

    @Column(name = "cancelled_by_type", length = 10)
    private String cancelledByType;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "actual_arrival_at")
    private OffsetDateTime actualArrivalAt;

    @Column(name = "is_priority", nullable = false)
    private Boolean isPriority;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private StaffAccount createdBy;
}

