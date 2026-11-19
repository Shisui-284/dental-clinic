package com.chia.dental.care;

import java.time.ZoneId;

public final class CareConstants {
    private CareConstants() {}

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    // Care_Task.Status (SRS 2.2.8)
    public static final String TASK_WAITING = "WAITING";
    public static final String TASK_IN_PROGRESS = "IN_PROGRESS";
    public static final String TASK_NEEDS_MEDICAL = "NEEDS_MEDICAL";
    public static final String TASK_CLOSED = "CLOSED";

    // Care_Task.Task_Type
    public static final String TYPE_POST_VISIT = "POST_VISIT_CHECK";
    public static final String TYPE_RECALL = "RECALL";
    public static final String TYPE_CONFIRM_CALL = "CONFIRM_CALL";

    public static final String SOURCE_SYSTEM = "SYSTEM";
    public static final String SOURCE_MANUAL = "MANUAL";

    // Complaint.Status
    public static final String CMP_OPEN = "OPEN";
    public static final String CMP_IN_PROGRESS = "IN_PROGRESS";
    public static final String CMP_WAITING_RESPONSE = "WAITING_RESPONSE";
    public static final String CMP_CLOSED = "CLOSED";

    // Kênh gửi tin
    public static final String CH_ZALO = "ZALO_ZNS";
    public static final String CH_SMS = "SMS";
    public static final String CH_ALL = "ALL";

    // Vai trò (UserPrincipal.role)
    public static final String ROLE_MANAGER = "MANAGER";
    public static final String ROLE_RECEPTIONIST = "RECEPTIONIST";
    public static final String ROLE_DOCTOR = "DOCTOR";
}
