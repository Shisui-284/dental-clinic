package com.chia.dental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "service")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "name", nullable = false, unique = true, length = 150)
    private String name;

    @Column(name = "duration", nullable = false)
    private Short duration;

    @Column(name = "price", precision = 12, scale = 0)
    private BigDecimal price;

    @Column(name = "care_scenario_id")
    private Long careScenarioId;

    @Column(name = "followup_cycle_days")
    private Integer followupCycleDays;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}

