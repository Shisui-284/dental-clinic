package com.chia.dental.treatment;

/** Phát sau khi lịch hẹn chuyển sang Đã hoàn tất (đã commit) — phân hệ CSKH lắng nghe để sinh công việc. */
public record AppointmentCompletedEvent(long appointmentId) {
}
