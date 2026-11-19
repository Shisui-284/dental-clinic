/**
 * MockAdapter — mô phỏng REST API 5.3 bằng dữ liệu cục bộ (localStorage).
 * Dùng khi config.mode = "mock" để frontend chạy được trước khi backend 7.1 sẵn sàng.
 * Cùng shape request/response với API thật => đổi mode sang "live" không phải sửa màn hình.
 */
import { ApiError } from "./http.js";
import { dateKey, pad, HOLD_MINUTES } from "./format.js";

const DB_KEY = "qldapm_mock_72";
const DAY = 86400000;

const SERVICES = [
  { id: "S01", group: "Tổng quát", name: "Khám tổng quát & tư vấn", duration: 30, price: 150000 },
  { id: "S02", group: "Tổng quát", name: "Cạo vôi răng + đánh bóng", duration: 45, price: 400000 },
  { id: "S03", group: "Tiểu phẫu", name: "Nhổ răng khôn (1 răng)", duration: 60, price: 1500000 },
  { id: "S04", group: "Tiểu phẫu", name: "Cắt nướu / điều trị nha chu", duration: 60, price: 1200000 },
  { id: "S05", group: "Phục hình", name: "Trám răng thẩm mỹ", duration: 40, price: 500000 },
  { id: "S06", group: "Phục hình", name: "Bọc răng sứ (1 răng)", duration: 90, price: 3500000 },
  { id: "S07", group: "Liệu trình dài", name: "Niềng răng — khám & chụp phim", duration: 60, price: 800000 },
  { id: "S08", group: "Liệu trình dài", name: "Tẩy trắng răng", duration: 75, price: 2500000 },
];

const DOCTORS = [
  { id: "D01", name: "BS. Trần Minh", title: "Nha khoa tổng quát", active: true, leave: [] },
  { id: "D02", name: "BS. Lê Thu Hà", title: "Nha khoa thẩm mỹ", active: true, leave: [] },
  { id: "D03", name: "BS. Nguyễn Quốc Bảo", title: "Tiểu phẫu", active: true, leave: [] },
  { id: "D04", name: "BS. Phạm Anh", title: "Chỉnh nha", active: false, leave: [] },
];

const CHAIRS = ["GH1", "GH2", "GH3", "GH4"];

const STAFF = [
  { id: "U01", username: "letan01", password: "demo1234", name: "Nguyễn Thu Hằng", role: "RECEPTIONIST" },
  { id: "U02", username: "bacsi01", password: "demo1234", name: "Trần Minh", role: "DOCTOR" },
  { id: "U03", username: "quanly01", password: "demo1234", name: "Phạm Quốc Bảo", role: "MANAGER" },
];

function isoAt(dayOffset, h, m = 0) {
  const d = new Date();
  d.setDate(d.getDate() + dayOffset);
  d.setHours(h, m, 0, 0);
  return d.toISOString();
}

function seed() {
  let seq = 120;
  const nextCode = () => `LH-2026-${String(++seq).padStart(6, "0")}`;
  const appts = [];

  const add = (o) => appts.push({
    id: "A" + (1000 + appts.length),
    code: nextCode(),
    chairId: CHAIRS[appts.length % CHAIRS.length],
    status: "CONFIRMED",
    source: "CUSTOMER",
    isPriority: false,
    holdUntil: null,
    note: "",
    cancelReason: null,
    createdAt: new Date(Date.now() - 3600000).toISOString(),
    ...o,
  });

  // Hôm nay — để L1/L3 có dữ liệu
  add({ patientName: "Phạm Ngọc Diệp", phone: "0912345678", serviceIds: ["S01"], doctorId: "D01", start: isoAt(0, 8, 30), duration: 30, status: "CHECKED_IN", source: "CUSTOMER" });
  add({ patientName: "Vũ Hoàng Nam", phone: "0908111222", serviceIds: ["S02"], doctorId: "D01", start: isoAt(0, 9, 15), duration: 45, status: "CONFIRMED", source: "RECEPTION" });
  add({ patientName: "Đỗ Thị Mai", phone: "0937444555", serviceIds: ["S05"], doctorId: "D02", start: isoAt(0, 10, 30), duration: 40, status: "CONFIRMED", source: "CUSTOMER" });
  add({ patientName: "Khách vãng lai", phone: "0977333444", serviceIds: ["S01"], doctorId: "D03", start: isoAt(0, 13, 30), duration: 30, status: "CHECKED_IN", source: "WALK_IN" });
  add({ patientName: "Lý Gia Hân", phone: "0966555666", serviceIds: ["S06"], doctorId: "D02", start: isoAt(0, 15, 0), duration: 90, status: "CONFIRMED", source: "CUSTOMER" });
  add({ patientName: "Nguyễn Văn Tú", phone: "0911222333", serviceIds: ["S03"], doctorId: "D03", start: isoAt(0, 16, 30), duration: 60, status: "NO_SHOW", source: "CUSTOMER" });

  // Chờ duyệt — L1
  add({ patientName: "Trần Bảo Châu", phone: "0988777888", serviceIds: ["S01", "S02"], doctorId: null, start: isoAt(1, 9, 0), duration: 75, status: "PENDING", source: "CUSTOMER", holdUntil: new Date(Date.now() + 18 * 60000).toISOString() });
  add({ patientName: "Hoàng Minh Anh", phone: "0903111999", serviceIds: ["S07"], doctorId: null, start: isoAt(1, 14, 0), duration: 60, status: "PENDING", source: "CUSTOMER", holdUntil: new Date(Date.now() + 6 * 60000).toISOString() });
  add({ patientName: "Phan Thanh Tùng", phone: "0944222000", serviceIds: ["S05"], doctorId: "D01", start: isoAt(1, 10, 0), duration: 40, status: "PENDING", source: "CUSTOMER", holdUntil: new Date(Date.now() - 5 * 60000).toISOString(), chairId: "GH2" });

  // Sắp tới — K7 của khách 0912345678
  add({ patientName: "Phạm Ngọc Diệp", phone: "0912345678", serviceIds: ["S05"], doctorId: "D02", start: isoAt(2, 10, 0), duration: 40, status: "CONFIRMED", source: "CUSTOMER", chairId: "GH1" });
  add({ patientName: "Phạm Ngọc Diệp", phone: "0912345678", serviceIds: ["S02"], doctorId: "D01", start: isoAt(10, 15, 0), duration: 45, status: "PENDING", source: "CUSTOMER", holdUntil: new Date(Date.now() + 25 * 60000).toISOString(), chairId: "GH3" });

  // Đã qua — K7 tab Đã qua
  add({ patientName: "Phạm Ngọc Diệp", phone: "0912345678", serviceIds: ["S01"], doctorId: "D01", start: isoAt(-7, 9, 0), duration: 30, status: "COMPLETED", source: "CUSTOMER" });
  add({ patientName: "Phạm Ngọc Diệp", phone: "0912345678", serviceIds: ["S08"], doctorId: "D02", start: isoAt(-20, 14, 0), duration: 75, status: "CANCELLED", source: "CUSTOMER", cancelReason: "Khách bận đột xuất" });

  return { seq, appointments: appts, blacklist: ["0999888777"], otp: {}, version: 1 };
}

function load() {
  try {
    const raw = localStorage.getItem(DB_KEY);
    if (raw) {
      const db = JSON.parse(raw);
      if (db && db.version === 1) return db;
    }
  } catch { /* ignore */ }
  const db = seed();
  save(db);
  return db;
}

function save(db) {
  try { localStorage.setItem(DB_KEY, JSON.stringify(db)); } catch { /* ignore */ }
}

export function resetMockData() {
  const db = seed();
  save(db);
}

const db = load();

function svc(id) { return SERVICES.find((s) => s.id === id); }
function byid(id) { return db.appointments.find((a) => a.id === id); }

function servicesOf(a) {
  return a.serviceIds.map(svc).filter(Boolean);
}

function durationOf(a) {
  return a.duration || servicesOf(a).reduce((t, s) => t + s.duration, 0) + 10;
}

function publicAppt(a) {
  const list = servicesOf(a);
  return {
    id: a.id,
    code: a.code,
    patientName: a.patientName,
    phone: a.phone,
    serviceIds: a.serviceIds,
    services: list.map((s) => ({ id: s.id, name: s.name, duration: s.duration, price: s.price })),
    doctorId: a.doctorId,
    doctorName: (DOCTORS.find((d) => d.id === a.doctorId) || {}).name || null,
    chairId: a.chairId,
    start: a.start,
    duration: durationOf(a),
    status: a.status,
    source: a.source,
    isPriority: a.isPriority,
    holdUntil: a.holdUntil,
    cancelReason: a.cancelReason,
    note: a.note,
    createdAt: a.createdAt,
  };
}

function withinBookingWindow(startIso) {
  const t = new Date(startIso).getTime();
  return t - Date.now() >= 2 * 3600 * 1000 && t - Date.now() <= 30 * DAY;
}

function conflicts(startIso, duration, doctorId, chairId, ignoreId) {
  const s = new Date(startIso).getTime();
  const e = s + duration * 60000;
  return db.appointments.some((a) => {
    if (a.id === ignoreId) return false;
    if (["CANCELLED", "NO_SHOW"].includes(a.status)) return false;
    const as = new Date(a.start).getTime();
    const ae = as + durationOf(a) * 60000;
    if (as >= e || ae <= s) return false;
    if (doctorId && a.doctorId === doctorId) return true;
    if (chairId && a.chairId === chairId) return true;
    return false;
  });
}

const OPEN = [[8, 0], [11, 30]];
const OPEN_PM = [[13, 30], [17, 30]];

function slotsFor(dayKey, durationMin, doctorId) {
  const day = new Date(dayKey + "T00:00:00");
  const out = [];
  const pushRange = (from, to) => {
    for (let m = from; m + durationMin <= to; m += 30) {
      const d = new Date(day);
      d.setHours(Math.floor(m / 60), m % 60, 0, 0);
      const iso = d.toISOString();
      if (!withinBookingWindow(iso)) continue;
      const doctors = doctorId ? [doctorId] : DOCTORS.filter((x) => x.active).map((x) => x.id);
      const free = doctors.some((doc) => CHAIRS.some((c) => !conflicts(iso, durationMin, doc, c)));
      out.push({ start: iso, available: free });
    }
  };
  pushRange(OPEN[0][0] * 60 + OPEN[0][1], OPEN[1][0] * 60 + OPEN[1][1]);
  pushRange(OPEN_PM[0][0] * 60 + OPEN_PM[0][1], OPEN_PM[1][0] * 60 + OPEN_PM[1][1]);
  return out;
}

function verifyOtp(phone, code) {
  const rec = db.otp[phone];
  if (!rec) throw new ApiError(400, "OTP_NOT_FOUND", "Chưa yêu cầu mã OTP cho số này.");
  if (Date.now() > rec.expiresAt) throw new ApiError(400, "OTP_EXPIRED", "Mã đã hết hạn. Vui lòng gửi lại.");
  if (code !== rec.code) {
    rec.tries += 1;
    if (rec.tries >= 5) throw new ApiError(429, "OTP_LOCKED", "Tạm khóa xác thực 30 phút. Gọi 1900 1234 nếu cần.");
    save(db);
    throw new ApiError(400, "OTP_INVALID", `Mã OTP không đúng. Bạn còn ${5 - rec.tries} lần thử.`);
  }
}

/** Bảng route mock: [method, regex, handler] */
const routes = [
  ["POST", /^\/api\/v1\/auth\/staff\/login$/, (m, body) => {
    const s = STAFF.find((x) => x.username === String(body.username || "").trim().toLowerCase());
    if (!s || s.password !== body.password) throw new ApiError(401, "E1", "Tên đăng nhập hoặc mật khẩu không đúng.");
    return { token: "mock." + s.id, staff: { id: s.id, name: s.name, role: s.role, username: s.username } };
  }],

  ["POST", /^\/api\/v1\/auth\/customer\/check-phone$/, (m, body) => {
    const phone = String(body.phone || "");
    if (!/^0\d{9}$/.test(phone)) throw new ApiError(422, "PHONE_INVALID", "SĐT phải gồm 10 chữ số, bắt đầu bằng 0.");
    const blacklisted = db.blacklist.includes(phone);
    const mine = db.appointments.filter((a) => a.phone === phone);
    return {
      phone,
      isBlacklisted: blacklisted,
      hasProfile: mine.length > 0,
      suggestedDoctorId: mine.length && mine[0].doctorId ? mine[0].doctorId : null,
      lastVisit: mine.length ? mine[mine.length - 1].start : null,
    };
  }],

  ["POST", /^\/api\/v1\/auth\/otp\/request$/, (m, body) => {
    const phone = String(body.phone || "");
    const prev = db.otp[phone];
    if (prev && Date.now() - prev.sentAt < 60000 && prev.sentAt > 0) {
      throw new ApiError(429, "OTP_RESEND_TOO_SOON", "Vui lòng chờ hết 60 giây trước khi gửi lại.");
    }
    db.otp[phone] = { code: "123456", sentAt: Date.now(), expiresAt: Date.now() + 5 * 60000, tries: 0 };
    save(db);
    return { sent: true, expiresIn: 300, resendAfter: 60, channel: "ZALO_ZNS" };
  }],

  ["POST", /^\/api\/v1\/auth\/otp\/verify$/, (m, body) => {
    const phone = String(body.phone || "");
    if (!body.consent) throw new ApiError(422, "CONSENT_REQUIRED", "Cần đồng ý xử lý dữ liệu cá nhân để tiếp tục.");
    verifyOtp(phone, String(body.code || ""));
    delete db.otp[phone];
    save(db);
    return { token: "mock.customer." + phone, customer: { phone, name: body.patient?.fullName || null } };
  }],

  ["GET", /^\/api\/v1\/public\/services$/, () => SERVICES.map((s) => ({ ...s, isActive: true }))],

  ["GET", /^\/api\/v1\/public\/doctors$/, () => DOCTORS.map((d) => ({ id: d.id, name: d.name, title: d.title, isActive: d.active }))],

  ["GET", /^\/api\/v1\/public\/appointments\/available-slots$/, (m, body, q) => {
    const date = q.get("date") || dateKey(new Date(Date.now() + DAY));
    const ids = (q.get("serviceIds") || "").split(",").filter(Boolean);
    const base = ids.reduce((t, id) => t + ((svc(id) || {}).duration || 0), 0);
    const duration = (base || 30) + 10;
    return { date, duration, slots: slotsFor(date, duration, q.get("doctorId") || null) };
  }],

  ["POST", /^\/api\/v1\/customer\/appointments$/, (m, body) => {
    const { phone, patient, serviceIds, doctorId, start } = body;
    if (!patient?.fullName || !patient?.birthYear) throw new ApiError(422, "PATIENT_REQUIRED", "Thiếu họ tên hoặc năm sinh người khám.");
    if (!serviceIds?.length) throw new ApiError(422, "SERVICE_REQUIRED", "Chọn ít nhất 1 dịch vụ.");
    if (!start) throw new ApiError(422, "TIME_REQUIRED", "Chưa chọn khung giờ.");
    if (!withinBookingWindow(start)) throw new ApiError(422, "OUT_OF_WINDOW", "Chỉ đặt được từ 2 giờ đến 30 ngày trước giờ hẹn.");
    const dur = serviceIds.reduce((t, id) => t + ((svc(id) || {}).duration || 0), 0) + 10 + (patient.isNew ? 15 : 0);
    const doc = doctorId || DOCTORS.find((d) => d.active).id;
    const chair = CHAIRS.find((c) => !conflicts(start, dur, doc, c));
    if (!chair) throw new ApiError(409, "SLOT_TAKEN", "Khung giờ vừa được người khác giữ. Vui lòng chọn giờ khác.");
    db.seq += 1;
    const appt = {
      id: "A" + Date.now(),
      code: `LH-2026-${String(db.seq).padStart(6, "0")}`,
      patientName: patient.fullName,
      phone,
      serviceIds,
      doctorId: doc,
      chairId: chair,
      start,
      duration: dur,
      status: "PENDING",
      source: "CUSTOMER",
      isPriority: false,
      holdUntil: new Date(Date.now() + HOLD_MINUTES * 60000).toISOString(),
      note: patient.note || "",
      cancelReason: null,
      createdAt: new Date().toISOString(),
    };
    db.appointments.push(appt);
    save(db);
    return { ...publicAppt(appt), notifyStatus: "SENT" };
  }],

  ["GET", /^\/api\/v1\/customer\/appointments$/, (m, body, q) => {
    const phone = q.get("phone");
    const scope = q.get("scope") || "upcoming";
    const now = Date.now();
    const list = db.appointments
      .filter((a) => a.phone === phone)
      .filter((a) => (scope === "past" ? new Date(a.start).getTime() < now : new Date(a.start).getTime() >= now))
      .sort((a, b) => (scope === "past" ? new Date(b.start) - new Date(a.start) : new Date(a.start) - new Date(b.start)))
      .map(publicAppt);
    return { scope, items: list };
  }],

  ["GET", /^\/api\/v1\/customer\/appointments\/([^/]+)\/ics$/, (m) => {
    const a = byid(m[1]);
    if (!a) throw new ApiError(404, "NOT_FOUND", "Không tìm thấy lịch hẹn.");
    const dt = new Date(a.start).toISOString().replace(/[-:]/g, "").split(".")[0] + "Z";
    return { text: `BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//AnTam//7.2//VI\nBEGIN:VEVENT\nUID:${a.id}\nDTSTART:${dt}\nSUMMARY:Khám tại Nha khoa An Tâm (${a.code})\nDESCRIPTION:${servicesOf(a).map((s) => s.name).join(", ")}\nEND:VEVENT\nEND:VCALENDAR\n` };
  }],

  ["PUT", /^\/api\/v1\/customer\/appointments\/([^/]+)\/reschedule$/, (m, body) => {
    const a = byid(m[1]);
    if (!a) throw new ApiError(404, "NOT_FOUND", "Không tìm thấy lịch hẹn.");
    if (!["PENDING", "CONFIRMED"].includes(a.status)) throw new ApiError(409, "NOT_EDITABLE", "Lịch này không thể đổi.");
    if (new Date(a.start).getTime() - Date.now() < 4 * 3600000) throw new ApiError(409, "TOO_LATE", "Chỉ có thể tự đổi trước giờ hẹn 4 giờ. Vui lòng gọi 1900 1234.");
    if (conflicts(body.start, durationOf(a), body.doctorId || a.doctorId, a.chairId, a.id)) throw new ApiError(409, "SLOT_TAKEN", "Khung giờ vừa được người khác giữ. Vui lòng chọn giờ khác.");
    a.start = body.start;
    if (body.doctorId) a.doctorId = body.doctorId;
    a.status = "PENDING";
    a.holdUntil = new Date(Date.now() + HOLD_MINUTES * 60000).toISOString();
    save(db);
    return publicAppt(a);
  }],

  ["PATCH", /^\/api\/v1\/customer\/appointments\/([^/]+)\/cancel$/, (m, body) => {
    const a = byid(m[1]);
    if (!a) throw new ApiError(404, "NOT_FOUND", "Không tìm thấy lịch hẹn.");
    if (!["PENDING", "CONFIRMED"].includes(a.status)) throw new ApiError(409, "NOT_CANCELLABLE", "Lịch này không thể hủy.");
    if (new Date(a.start).getTime() - Date.now() < 4 * 3600000) throw new ApiError(409, "TOO_LATE", "Chỉ có thể tự hủy trước giờ hẹn 4 giờ. Vui lòng gọi 1900 1234.");
    a.status = "CANCELLED";
    a.cancelReason = body.reason || "Khách tự hủy";
    save(db);
    return publicAppt(a);
  }],

  ["GET", /^\/api\/v1\/staff\/appointments$/, (m, body, q) => {
    const date = q.get("date");
    const status = q.get("status");
    const doctorId = q.get("doctorId");
    const source = q.get("source");
    const items = db.appointments
      .filter((a) => (date ? dateKey(a.start) === date : true))
      .filter((a) => (status ? a.status === status : true))
      .filter((a) => (doctorId ? a.doctorId === doctorId : true))
      .filter((a) => (source ? a.source === source : true))
      .sort((a, b) => new Date(a.start) - new Date(b.start))
      .map(publicAppt);
    return { items };
  }],

  ["POST", /^\/api\/v1\/staff\/appointments$/, (m, body) => {
    const { phone, patient, serviceIds, temporaryService, doctorId, start, source, isPriority, note } = body;
    if (!patient?.fullName) throw new ApiError(422, "PATIENT_REQUIRED", "Thiếu họ tên người khám.");
    if (!/^0\d{9}$/.test(String(phone || ""))) throw new ApiError(422, "PHONE_INVALID", "SĐT phải gồm 10 chữ số.");
    if (!serviceIds?.length && !temporaryService) throw new ApiError(422, "SERVICE_REQUIRED", "Chọn ít nhất 1 dịch vụ hoặc dịch vụ tạm.");
    const effectiveStart = start || new Date().toISOString();
    const officialDur = (serviceIds || []).reduce((t, id) => t + ((svc(id) || {}).duration || 0), 0);
    const tempDur = temporaryService ? (Number(temporaryService.durationMinutes) || 30) : 0;
    const dur = officialDur + tempDur + 5 + (patient.isNew ? 15 : 0);
    const doc = doctorId || DOCTORS.find((d) => d.active).id;
    const chair = CHAIRS.find((c) => !conflicts(effectiveStart, dur, doc, c)) || CHAIRS[0];
    if (!chair) throw new ApiError(409, "SLOT_TAKEN", "Khung giờ đã kín. Chọn khung khác.");
    db.seq += 1;
    const status = source === "WALK_IN" ? "CHECKED_IN" : "CONFIRMED";
    const appt = {
      id: "A" + Date.now(),
      code: `LH-2026-${String(db.seq).padStart(6, "0")}`,
      patientName: patient.fullName,
      phone,
      serviceIds: serviceIds || [],
      temporaryService: temporaryService || null,
      doctorId: doc,
      chairId: chair,
      start: effectiveStart,
      duration: dur,
      status,
      source: source || "RECEPTION",
      isPriority: !!isPriority,
      holdUntil: null,
      note: note || (temporaryService?.description ? `[Dịch vụ tạm: ${temporaryService.description}]` : ""),
      cancelReason: null,
      createdAt: new Date().toISOString(),
    };
    db.appointments.push(appt);
    save(db);
    return publicAppt(appt);
  }],

  ["PATCH", /^\/api\/v1\/staff\/appointments\/([^/]+)\/confirm$/, (m, body) => {
    const a = byid(m[1]);
    if (!a) throw new ApiError(404, "NOT_FOUND", "Không tìm thấy lịch hẹn.");
    if (a.status !== "PENDING") throw new ApiError(409, "NOT_PENDING", "Chỉ duyệt được lịch đang chờ xác nhận.");
    if (a.holdUntil && new Date(a.holdUntil).getTime() < Date.now()) throw new ApiError(409, "HOLD_EXPIRED", "Lịch đã hết hạn giữ chỗ — cần từ chối và hẹn lại.");
    if (!body.doctorId) throw new ApiError(422, "DOCTOR_REQUIRED", "Chọn bác sĩ trước khi duyệt.");
    if (conflicts(a.start, durationOf(a), body.doctorId, null, a.id)) throw new ApiError(409, "DOCTOR_BUSY", "Bác sĩ đã có lịch trong khung này.");
    a.doctorId = body.doctorId;
    if (body.chairId) a.chairId = body.chairId;
    a.status = "CONFIRMED";
    a.holdUntil = null;
    save(db);
    return publicAppt(a);
  }],

  ["PUT", /^\/api\/v1\/staff\/appointments\/([^/]+)\/reschedule$/, (m, body) => {
    const a = byid(m[1]);
    if (!a) throw new ApiError(404, "NOT_FOUND", "Không tìm thấy lịch hẹn.");
    if (["COMPLETED", "CANCELLED"].includes(a.status)) throw new ApiError(409, "NOT_EDITABLE", "Lịch này không thể đổi.");
    if (conflicts(body.start, durationOf(a), body.doctorId || a.doctorId, body.chairId || a.chairId, a.id)) throw new ApiError(409, "SLOT_TAKEN", "Khung giờ đã kín.");
    a.start = body.start;
    if (body.doctorId) a.doctorId = body.doctorId;
    if (body.chairId) a.chairId = body.chairId;
    save(db);
    return publicAppt(a);
  }],

  ["PATCH", /^\/api\/v1\/staff\/appointments\/([^/]+)\/cancel$/, (m, body) => {
    const a = byid(m[1]);
    if (!a) throw new ApiError(404, "NOT_FOUND", "Không tìm thấy lịch hẹn.");
    if (!body.cancelReason) throw new ApiError(422, "REASON_REQUIRED", "Bắt buộc nhập lý do hủy.");
    a.status = "CANCELLED";
    a.cancelReason = body.cancelReason;
    save(db);
    return publicAppt(a);
  }],

  ["PATCH", /^\/api\/v1\/staff\/appointments\/([^/]+)\/check-in$/, (m) => {
    const a = byid(m[1]);
    if (!a) throw new ApiError(404, "NOT_FOUND", "Không tìm thấy lịch hẹn.");
    if (!["CONFIRMED", "NO_SHOW"].includes(a.status)) throw new ApiError(409, "BAD_TRANSITION", "Chỉ check-in được lịch đã xác nhận.");
    a.status = "CHECKED_IN";
    a.arrivedAt = new Date().toISOString();
    save(db);
    return publicAppt(a);
  }],

  ["PATCH", /^\/api\/v1\/staff\/appointments\/([^/]+)\/no-show$/, (m) => {
    const a = byid(m[1]);
    if (!a) throw new ApiError(404, "NOT_FOUND", "Không tìm thấy lịch hẹn.");
    if (!["CONFIRMED", "PENDING"].includes(a.status)) throw new ApiError(409, "BAD_TRANSITION", "Chỉ đánh dấu Không đến cho lịch đã xác nhận.");
    a.status = "NO_SHOW";
    save(db);
    return publicAppt(a);
  }],

  ["POST", /^\/api\/v1\/staff\/appointments\/([^/]+)\/reverse-no-show$/, (m, body) => {
    const a = byid(m[1]);
    if (!a) throw new ApiError(404, "NOT_FOUND", "Không tìm thấy lịch hẹn.");
    if (a.status !== "NO_SHOW") throw new ApiError(409, "BAD_TRANSITION", "Chỉ hoàn tác được lịch Không đến.");
    if (!body.reversedReason) throw new ApiError(422, "REASON_REQUIRED", "Bắt buộc nhập lý do hoàn tác.");
    a.status = "CHECKED_IN";
    a.reversedReason = body.reversedReason;
    save(db);
    return publicAppt(a);
  }],

  ["POST", /^\/api\/v1\/auth\/staff\/logout$/, () => ({ ok: true })],

  ["GET", /^\/api\/v1\/staff\/chairs$/, () => CHAIRS.map((c) => ({ id: c, name: `Ghế ${c}`, status: "ACTIVE" }))],

  ["GET", /^\/api\/v1\/staff\/schedules$/, (m, body, q) => {
    const date = q.get("date");
    const leaves = (db.leaves || [])
      .filter((l) => l.status === "APPROVED" && (!date || l.leaveDate === date))
      .map((l) => ({ doctorId: l.staffId, doctorName: l.doctorName, date: l.leaveDate, start: l.startTime, end: l.endTime, reason: l.reason }));
    return { date, leaves };
  }],

  ["GET", /^\/api\/v1\/staff\/doctor-schedules$/, () => {
    const today = dateKey(new Date());
    const tomorrow = dateKey(new Date(Date.now() + DAY));
    return [
      { id: "SCH1", staffId: "D01", doctorName: "BS. Trần Minh", shiftDate: today, startTime: "08:00", endTime: "17:30", status: "ACTIVE" },
      { id: "SCH2", staffId: "D01", doctorName: "BS. Trần Minh", shiftDate: tomorrow, startTime: "08:00", endTime: "17:30", status: "ACTIVE" },
    ];
  }],

  ["POST", /^\/api\/v1\/doctor\/leaves$/, (m, body) => {
    db.leaves = db.leaves || [];
    const lv = {
      id: "LV" + Date.now(),
      staffId: body.staffId || "D01",
      doctorName: (DOCTORS.find((d) => d.id === (body.staffId || "D01")) || DOCTORS[0]).name,
      leaveDate: body.leaveDate,
      startTime: String(body.startTime || "08:00").slice(0, 5),
      endTime: String(body.endTime || "12:00").slice(0, 5),
      reason: body.reason || "Nghỉ đột xuất",
      status: "PENDING",
      approvedByName: null,
    };
    db.leaves.unshift(lv);
    save(db);
    return lv;
  }],

  ["GET", /^\/api\/v1\/staff\/leaves$/, (m, body, q) => {
    db.leaves = db.leaves || [];
    const st = q.get("status");
    return db.leaves.filter((l) => (!st ? true : l.status === st));
  }],

  ["PATCH", /^\/api\/v1\/manager\/leaves\/([^/]+)\/approval$/, (m, body) => {
    db.leaves = db.leaves || [];
    const lv = db.leaves.find((l) => l.id === m[1]);
    if (!lv) throw new ApiError(404, "NOT_FOUND", "Không tìm thấy đơn nghỉ.");
    lv.status = body.decision;
    lv.approvedByName = "Phạm Quốc Bảo";
    save(db);
    const affected = lv.status === "APPROVED"
      ? db.appointments.filter((a) => a.status === "CONFIRMED" && a.doctorId === lv.staffId && dateKey(a.start) === lv.leaveDate)
      : [];
    return { leave: lv, conflictCount: affected.length, affectedAppointments: affected };
  }],

  ["GET", /^\/api\/v1\/staff\/appointments\/leave-conflicts$/, () => {
    const approved = (db.leaves || []).filter((l) => l.status === "APPROVED");
    const out = [];
    for (const lv of approved) {
      for (const a of db.appointments) {
        if (a.status === "CONFIRMED" && a.doctorId === lv.staffId && dateKey(a.start) === lv.leaveDate) {
          out.push({
            appointmentId: a.id,
            appointmentCode: a.code,
            patientName: a.patientName,
            phone: a.phone,
            doctorName: lv.doctorName,
            dateTime: a.start,
            leaveId: lv.id,
            leaveDate: lv.leaveDate,
            leaveStartTime: lv.startTime,
            leaveEndTime: lv.endTime,
            leaveReason: lv.reason,
          });
        }
      }
    }
    return out;
  }],

  ["GET", /^\/api\/v1\/manager\/blacklist$/, () => (db.blacklist || []).map((phone, i) => ({
    customerId: String(100 + i),
    phone,
    patientNames: ["Khách vi phạm (Mock)"],
    activeNoShowCount: 3,
    threshold: 3,
    windowMonths: 12,
    stillExceedsThreshold: true,
    isBlacklisted: true,
  }))],

  ["POST", /^\/api\/v1\/manager\/blacklist\/([^/]+)\/unlock$/, (m, body) => {
    if (!body.reason) throw new ApiError(422, "REASON_REQUIRED", "Bắt buộc nhập lý do mở khóa.");
    const idx = Number(m[1]) - 100;
    if (idx >= 0 && idx < (db.blacklist || []).length) {
      db.blacklist.splice(idx, 1);
      save(db);
    }
    return { isBlacklisted: false, warning: "Đã mở khóa đặc cách trong chế độ Mock." };
  }],
];

export async function mockRequest(method, path, { body, query } = {}) {
  await new Promise((r) => setTimeout(r, 120));
  const q = query instanceof URLSearchParams ? query : new URLSearchParams(query || {});
  for (const [m, re, fn] of routes) {
    if (m !== method) continue;
    const match = path.match(re);
    if (!match) continue;
    return fn(match, body || {}, q);
  }
  throw new ApiError(404, "ROUTE_NOT_FOUND", `Mock chưa hỗ trợ ${method} ${path}`);
}

export { SERVICES, DOCTORS, CHAIRS, STAFF, publicAppt };
