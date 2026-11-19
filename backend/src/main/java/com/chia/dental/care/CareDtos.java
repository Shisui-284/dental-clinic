package com.chia.dental.care;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class CareDtos {
    private CareDtos() {}

    // ============================ chung ============================
    public record PageResult<T>(List<T> items, long total, int page, int size) {}

    // ============================ công việc CSKH ============================
    public record CareTaskView(
            Long taskId, String taskType, String source, String status,
            boolean overdue, long overdueMinutes,
            OffsetDateTime scheduledAt, OffsetDateTime visibleFrom, OffsetDateTime slaDeadline,
            OffsetDateTime createdAt, OffsetDateTime completedAt,
            String resultNote, String medicalRequestNote, String medicalResponseNote,
            String scenarioCategory,
            Long appointmentId, OffsetDateTime appointmentTime, Long doctorId, String doctorName,
            Long patientId, String patientName, Long customerId, String phone, boolean optedOut,
            Long assigneeId, String assigneeName) {}

    public record CloseTaskRequest(
            @NotBlank(message = "Bắt buộc nhập kết quả xử lý (Result_Note)")
            @Size(max = 2000) String resultNote) {}

    public record MedicalSupportRequest(
            @NotBlank(message = "Bắt buộc nhập nội dung cần bác sĩ hỗ trợ")
            @Size(max = 2000) String note) {}

    public record MedicalResponseRequest(
            @NotBlank(message = "Bắt buộc nhập phản hồi chuyên môn")
            @Size(max = 2000) String note) {}

    public record ConfirmCallRequest(
            @NotBlank(message = "Bắt buộc ghi kết quả cuộc gọi")
            @Size(max = 2000) String resultNote) {}

    public record UpcomingAppointmentView(
            Long appointmentId, OffsetDateTime dateTime, String status, String note,
            Long patientId, String patientName, Integer patientAge, Long customerId, String phone,
            String doctorName, String services, boolean hasMinorSurgery,
            boolean confirmedByCall, boolean optedOut) {}

    // ============================ khách hàng (Customer 360 phần CSKH) ============================
    public record CustomerSummary(Long customerId, String phone, boolean blacklisted, String patientNames) {}

    public record PatientBrief(Long patientId, String fullName, Short birthYear, String gender) {}

    public record OptOutView(Long optOutId, String channel, List<String> excludedNotifTypes, String reason,
                             OffsetDateTime createdAt, Long createdBy) {}

    public record VisitBrief(Long appointmentId, OffsetDateTime dateTime, String doctorName, String patientName,
                             String services, String status) {}

    public record NotificationLogView(Long notifId, Long customerId, Long appointmentId, Long careTaskId,
                                      String notifType, String channel, String recipientPhone, String content,
                                      String status, String errorCode, String providerMsgId,
                                      OffsetDateTime createdAt, OffsetDateTime sentAt) {}

    public record CustomerCareOverview(
            Long customerId, String phone, boolean blacklisted, OffsetDateTime createdAt,
            List<PatientBrief> patients,
            Map<String, Long> appointmentCounts, long activeNoShowCount,
            List<OptOutView> activeOptOuts,
            List<VisitBrief> recentVisits,
            List<CareTaskView> recentTasks,
            List<NotificationLogView> recentNotifications,
            List<FeedbackView> recentFeedback) {}

    public record OptOutRequest(
            @NotNull(message = "Bắt buộc chọn bật/tắt từ chối nhận tin") Boolean optedOut,
            @Size(max = 255) String reason,
            /** ALL (mặc định) | ZALO_ZNS | SMS */
            String channel) {}

    // ============================ phản hồi & khiếu nại ============================
    public record FeedbackView(
            Long feedbackId, Long appointmentId, Long patientId, String patientName, Long customerId,
            Long doctorId, String doctorName, Short ratingScore, String comment,
            boolean alert, boolean hidden, OffsetDateTime createdAt,
            Long complaintId, String complaintStatus) {}

    public record HideRequest(
            @NotBlank(message = "Bắt buộc nhập lý do ẩn") @Size(max = 500) String reason) {}

    public record ComplaintView(
            Long complaintId, Long patientId, String patientName, String phone,
            Long feedbackId, Short ratingScore, String content, String severity,
            Long handlerId, String handlerName, String actionTaken, String result, String status,
            OffsetDateTime slaDeadline, boolean overdue, boolean hidden,
            OffsetDateTime createdAt, OffsetDateTime closedAt) {}

    public record CreateComplaintRequest(
            @NotNull Long patientId,
            Long feedbackId,
            @NotBlank @Size(max = 2000) String content,
            /** LOW | MEDIUM | HIGH (mặc định MEDIUM) */
            String severity) {}

    public record ComplaintStatusRequest(
            @NotBlank(message = "Bắt buộc chọn trạng thái đích") String status,
            @Size(max = 2000) String actionTaken,
            @Size(max = 2000) String result) {}

    // ============================ đánh giá công khai (K9) ============================
    public record FeedbackLinkInfo(String clinicName, String hotline, OffsetDateTime appointmentTime,
                                   String doctorName, String services) {}

    public record SubmitFeedbackRequest(
            @NotNull(message = "Vui lòng chọn số sao")
            @Min(value = 1, message = "Số sao từ 1 đến 5") @Max(value = 5, message = "Số sao từ 1 đến 5") Integer rating,
            @Size(max = 1000, message = "Bình luận tối đa 1.000 ký tự") String comment) {}

    public record SubmitFeedbackResponse(boolean needsAttention, String message) {}

    // ============================ dashboard & báo cáo ============================
    public record DashboardView(
            long waitingTasks, long inProgressTasks, long needsMedicalTasks, long overdueTasks,
            long openComplaints, long overdueComplaints, long alertFeedbacks, long failedNotifications24h,
            List<FeedbackView> redAlerts, List<FeedbackView> latestFeedback) {}

    public record CareReportSummary(
            OffsetDateTime from, OffsetDateTime to,
            Map<String, Long> tasksByStatus, Map<String, Long> tasksByType, long overdueOpenTasks,
            Map<String, Long> notificationsByChannelAndStatus, Double notificationSuccessRate,
            long feedbackCount, Double averageRating, long alertFeedbackCount,
            Map<String, Long> complaintsByStatus, List<CareTaskView> overdueTasks) {}
}
