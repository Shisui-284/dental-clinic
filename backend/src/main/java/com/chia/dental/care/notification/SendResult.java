package com.chia.dental.care.notification;

/**
 * Kết quả một lần gọi nhà cung cấp (NFR-10: chỉ SUCCESS khi nhà cung cấp xác nhận).
 * REJECTED: nhà cung cấp trả lỗi rõ ràng (vd. SĐT không dùng Zalo) -> đủ điều kiện chuyển kênh dự phòng.
 * UNKNOWN: không biết tin đã đi hay chưa (timeout, 5xx, 429) -> thử lại trước, tránh gửi trùng.
 */
public record SendResult(Outcome outcome, String providerMsgId, String errorCode, String errorMessage) {

    public enum Outcome { SUCCESS, REJECTED, UNKNOWN }

    public static SendResult success(String providerMsgId) {
        return new SendResult(Outcome.SUCCESS, providerMsgId, null, null);
    }

    public static SendResult rejected(String code, String message) {
        return new SendResult(Outcome.REJECTED, null, code, message);
    }

    public static SendResult unknown(String code, String message) {
        return new SendResult(Outcome.UNKNOWN, null, code, message);
    }

    public boolean isSuccess() {
        return outcome == Outcome.SUCCESS;
    }

    public boolean isUnknown() {
        return outcome == Outcome.UNKNOWN;
    }

    public String describe() {
        String msg = errorMessage == null ? "" : ": " + errorMessage;
        return (errorCode == null ? "ERROR" : errorCode) + msg;
    }
}
