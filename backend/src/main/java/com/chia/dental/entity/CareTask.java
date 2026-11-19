package com.chia.dental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** Công việc CSKH (SRS 4.1 Care_Task). Trạng thái: WAITING → IN_PROGRESS → NEEDS_MEDICAL → CLOSED. */
@Entity
@Table(name = "care_task")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "task_type", nullable = false, length = 20)
    private String taskType;

    @Column(name = "source", nullable = false, length = 10)
    private String source;

    @Column(name = "care_scenario_id")
    private Long careScenarioId;

    @Column(name = "assignee_id", nullable = false)
    private Long assigneeId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;

    @Column(name = "visible_from", nullable = false)
    private OffsetDateTime visibleFrom;

    @Column(name = "sla_deadline", nullable = false)
    private OffsetDateTime slaDeadline;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "is_overdue", nullable = false)
    private Boolean isOverdue;

    @Column(name = "result_note")
    private String resultNote;

    @Column(name = "medical_request_note")
    private String medicalRequestNote;

    @Column(name = "medical_response_note")
    private String medicalResponseNote;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
