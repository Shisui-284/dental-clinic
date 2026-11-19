/** Định dạng ngày giờ, tiền, SĐT, nhãn trạng thái — theo 5.2 §7.5 */

export const VND = new Intl.NumberFormat("vi-VN");

export function money(n) {
  return VND.format(Math.round(Number(n) || 0)) + " ₫";
}

export function pad(n) {
  return String(n).padStart(2, "0");
}

export function dateKey(d) {
  const x = new Date(d);
  return `${x.getFullYear()}-${pad(x.getMonth() + 1)}-${pad(x.getDate())}`;
}

export function fmtDate(d) {
  if (!d) return "—";
  const x = new Date(d);
  if (Number.isNaN(x.getTime())) return "—";
  return `${pad(x.getDate())}/${pad(x.getMonth() + 1)}/${x.getFullYear()}`;
}

export function fmtTime(d) {
  if (!d) return "—";
  const x = new Date(d);
  return `${pad(x.getHours())}:${pad(x.getMinutes())}`;
}

export function fmtDateTime(d) {
  return `${fmtDate(d)} ${fmtTime(d)}`;
}

export function fmtRange(start, durationMin) {
  const end = new Date(new Date(start).getTime() + durationMin * 60000);
  return `${fmtTime(start)}–${fmtTime(end)}`;
}

export function weekday(d) {
  return ["CN", "T2", "T3", "T4", "T5", "T6", "T7"][new Date(d).getDay()];
}

/** "Còn 2 giờ 15 phút" | "Còn 42 phút" | "Đã quá hạn 1 giờ" */
export function countdown(target, now = Date.now()) {
  const ms = new Date(target).getTime() - now;
  const abs = Math.abs(ms);
  const mins = Math.floor(abs / 60000);
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  let text;
  if (h >= 24) text = `${Math.floor(h / 24)} ngày ${h % 24} giờ`;
  else if (h > 0) text = `${h} giờ ${m} phút`;
  else text = `${m} phút`;
  return ms < 0 ? `Đã quá hạn ${text}` : `Còn ${text}`;
}

export function shortPhone(p) {
  const s = String(p || "");
  if (s.length < 7) return s;
  return `${s.slice(0, 4)} *** ${s.slice(-3)}`;
}

export const STATUS = {
  PENDING: { label: "Chờ xác nhận", cls: "pending", note: "Lễ tân sẽ xác nhận trước hạn giữ chỗ." },
  CONFIRMED: { label: "Đã xác nhận", cls: "confirmed", note: "Lịch đã được xác nhận. Vui lòng đến trước 10 phút." },
  CHECKED_IN: { label: "Đã đến", cls: "checked", note: "Đã tiếp nhận. Vui lòng chờ gọi tên." },
  COMPLETED: { label: "Đã hoàn tất", cls: "done", note: "Buổi khám đã hoàn tất." },
  CANCELLED: { label: "Đã hủy", cls: "cancelled", note: "Lịch đã hủy; khung giờ đã được giải phóng." },
  NO_SHOW: { label: "Không đến", cls: "noshow", note: "Không ghi nhận khách đến." },
};

export const SOURCE = {
  CUSTOMER: "Khách tự đặt",
  RECEPTION: "Lễ tân",
  SOCIAL: "MXH",
  WALK_IN: "Vãng lai",
  EMERGENCY: "Cấp cứu",
  FOLLOW_UP: "Tái khám",
};

export function statusMeta(code) {
  return STATUS[code] || { label: code || "—", cls: "cancelled", note: "" };
}

export function statusTag(code) {
  const m = statusMeta(code);
  return `<span class="tag ${m.cls}">${m.label}</span>`;
}

export function sourceLabel(code) {
  return SOURCE[code] || code || "—";
}

export const MIN_BOOKING_MS = 4 * 3600 * 1000; // quy tắc đổi/hủy trước 4 giờ
export const HOLD_MINUTES = 30;
