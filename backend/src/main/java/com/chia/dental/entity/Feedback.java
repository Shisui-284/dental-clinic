package com.chia.dental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** Đánh giá của khách (SRS 4.1 Feedback). Không xóa vật lý — chỉ ẩn (FR-33). */
@Entity
@Table(name = "feedback")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedback_id")
    private Long feedbackId;

    @Column(name = "appointment_id", nullable = false, unique = true)
    private Long appointmentId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "rating_score", nullable = false)
    private Short ratingScore;

    @Column(name = "comment")
    private String comment;

    @Column(name = "alert_flag", nullable = false)
    private Boolean alertFlag;

    @Column(name = "is_hidden", nullable = false)
    private Boolean isHidden;

    @Column(name = "hidden_by")
    private Long hiddenBy;

    @Column(name = "hidden_at")
    private OffsetDateTime hiddenAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
