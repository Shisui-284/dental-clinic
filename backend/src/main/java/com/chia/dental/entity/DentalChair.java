package com.chia.dental.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "dental_chair")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DentalChair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chair_id")
    private Long chairId;

    @Column(name = "chair_name", nullable = false, unique = true, length = 50)
    private String chairName;

    @Column(name = "status", nullable = false, length = 15)
    private String status;
}

