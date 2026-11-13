package com.chia.dental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "appointment_service")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appt_service_id")
    private Long apptServiceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private ServiceEntity service;

    @Column(name = "is_temporary", nullable = false)
    private Boolean isTemporary;

    @Column(name = "duration_snapshot", nullable = false)
    private Short durationSnapshot;

    @Column(name = "price_snapshot", precision = 12, scale = 0)
    private BigDecimal priceSnapshot;

    @Column(name = "is_performed", nullable = false)
    private Boolean isPerformed;
}

