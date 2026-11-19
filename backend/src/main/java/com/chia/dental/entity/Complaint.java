package com.chia.dental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** Khiếu nại / ticket xử lý phản hồi (SRS 4.1 Complaint). Không xóa vật lý — chỉ ẩn (FR-33). */
@Entity
@Table(name = "complaint")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "complaint_id")
    private Long complaintId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "feedback_id")
    private Long feedbackId;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "severity", nullable = false, length = 10)
    private String severity;

    @Column(name = "handler_id")
    private Long handlerId;

    @Column(name = "action_taken")
    private String actionTaken;

    @Column(name = "result")
    private String result;

    @Column(name = "status", nullable = false, length = 18)
    private String status;

    @Column(name = "sla_deadline")
    private OffsetDateTime slaDeadline;

    @Column(name = "is_hidden", nullable = false)
    private Boolean isHidden;

    @Column(name = "hidden_by")
    private Long hiddenBy;

    @Column(name = "hidden_at")
    private OffsetDateTime hiddenAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;
}
