/**
 * LiveAdapter — nối frontend 7.2 với backend 7.1 (Spring Boot, API Contract 5.3).
 *
 * Nhiệm vụ duy nhất: chuyển đổi shape nội bộ mà các màn hình đang dùng
 * (id dạng chuỗi, `start`, `duration`, `items`, `leaves`...) sang/khỏi
 * shape thật của backend (appointmentId, dateTime, totalCalculatedDuration...).
 *
 * Nhờ vậy màn hình không phải sửa khi đổi Mock ↔ Live; mọi khác biệt hợp đồng
 * được xử lý tại đây. Backend trả Response Envelope đã được http.js bóc sẵn.
 */
import { get, post, put, patch } from "../core/http.js";

const CLINIC_OFFSET = "+07:00"; // Asia/Ho_Chi_Minh, Việt Nam không có DST

const num = (v) => {
  if (v === null || v === undefined || v === "") return undefined;
  const n = Number(v);
  return Number.isFinite(n) ? n : undefined;
};
const str = (v) => (v === null || v === undefined ? null : String(v));
const list = (v) => (Array.isArray(v) ? v : []);

/** Backend source (ONLINE|PHONE|SOCIAL|WALK_IN) → nhãn nguồn của frontend. */
function mapSource(source) {
  if (source === "ONLINE") return "CUSTOMER";
  if (source === "PHONE") return "RECEPTION";
  return source || "CUSTOMER";
}

function mapService(s) {
  return {
    id: String(s.serviceId),
    name: s.name,
    group: "Dịch vụ",
    duration: Number(s.duration) || 0,
    price: Number(s.price) || 0,
    followupCycleDays: s.followupCycleDays ?? null,
    isActive: true,
  };
}

function mapDoctor(d) {
  return {
    id: String(d.staffId),
    name: d.fullName || d.username,
    title: "Bác sĩ",
    username: d.username,
    isActive: true,
  };
}

function mapServiceItem(s, index) {
  return {
    id: s.serviceId != null ? String(s.serviceId) : `TEMP-${index}`,
    name: s.serviceName || "Dịch vụ tạm",
    duration: Number(s.durationSnapshot) || 0,
    price: Number(s.priceSnapshot) || 0,
    isTemporary: !!s.isTemporary,
  };
}

function mapAppointment(a) {
  const services = list(a.services).map(mapServiceItem);
  return {
    id: String(a.appointmentId),
    code: a.appointmentCode,
    patientName: a.patientName,
    phone: a.bookedByPhone,
    serviceIds: services.filter((s) => !s.isTemporary).map((s) => s.id),
    services,
    doctorId: str(a.doctorId),
    doctorName: a.doctorName || null,
    doctorAutoAssigned: !!a.doctorAutoAssigned,
    chairId: a.chairName || str(a.chairId),
    start: a.dateTime,
    duration: Number(a.duration) || 0,
    endTime: a.endTime,
    status: a.status,
    source: mapSource(a.source),
    isPriority: !!a.isPriority,
    holdUntil: a.holdUntil || null,
    cancelReason: a.cancelReason || null,
    note: a.note || "",
    createdAt: a.createdAt,
    actualArrivalAt: a.actualArrivalAt || null,
    canModify: !!a.canModify,
    hasLeaveConflict: !!a.hasLeaveConflict,
  };
}

/** "08:30" + "2026-11-20" → ISO có offset cố định +07:00. */
function atClinicTime(dateKey, hhmm) {
  const time = String(hhmm || "00:00").slice(0, 5);
  return `${dateKey}T${time}:00${CLINIC_OFFSET}`;
}

function mapSlots(data) {
  const dateKey = data?.date || data?.days?.[0]?.date;
  const raw = list(data?.days?.[0]?.slots).length ? data.days[0].slots : list(data?.slots);
  const slots = raw.map((s) => ({
    start: atClinicTime(dateKey, s.startTime),
    end: s.endTime ? atClinicTime(dateKey, s.endTime) : null,
    available: !!s.available,
    doctorId: str(s.doctorId),
    doctorName: s.doctorName || null,
    availableChairId: s.availableChairId ?? null,
  }));
  return {
    date: dateKey,
    duration: Number(data?.totalCalculatedDuration ?? data?.totalDurationMinutes ?? data?.duration) || 0,
    isNewPatient: !!data?.isNewPatient,
    holiday: !!data?.holiday,
    holidayName: data?.holidayName || null,
    slots,
  };
}

function patientInfoOf(patient) {
  if (!patient?.fullName) return undefined;
  return {
    fullName: patient.fullName,
    birthYear: Number(patient.birthYear),
    ...(patient.relation ? { relation: patient.relation } : {}),
    ...(patient.isGuardian ? { isGuardian: true } : {}),
  };
}

function serviceIdsOf(ids) {
  return list(ids).map(num).filter((x) => x !== undefined);
}

/** Lễ tân L2: nguồn của UI → source hợp lệ của backend. */
function staffSourceOf(source) {
  if (source === "WALK_IN") return "WALK_IN";
  return "PHONE"; // RECEPTION / EMERGENCY / FOLLOW_UP đều là lịch đặt qua lễ tân
}

export const live = {
  /* ---------- Auth / OTP ---------- */
  async staffLogin(username, password) {
    const r = await post("/api/v1/auth/staff/login", { username, password });
    return {
      token: r.accessToken,
      staff: {
        id: str(r.userId),
        name: r.fullName || r.username,
        role: r.role,
        username: r.username,
      },
    };
  },

  async checkPhone(phone) {
    const r = await post("/api/v1/auth/customer/check-phone", { phone });
    return {
      phone: r.phone || phone,
      isBlacklisted: !!r.blacklisted,
      hasProfile: !!r.existingCustomer,
      suggestedDoctorId: str(r.suggestedDoctorId),
      suggestedDoctorName: r.suggestedDoctorName || null,
      lastVisit: null,
      patients: list(r.patients),
    };
  },

  async requestOtp(phone) {
    const r = await post("/api/v1/auth/otp/request", { phone, purpose: "BOOKING" });
    return {
      sent: true,
      expiresIn: (Number(r.expireMinutes) || 5) * 60,
      resendAfter: 60,
      channel: "ZALO_ZNS",
      prototypeOtp: r.prototypeOtp || null,
    };
  },

  async verifyOtp(payload) {
    const r = await post("/api/v1/auth/otp/verify", {
      phone: payload.phone,
      otp: payload.code,
      purpose: "BOOKING",
      consent: {
        accepted: !!payload.consent,
        policyVersion: "v1.0-2026",
        channel: "WEB_PORTAL",
        consentedBy: payload.phone,
        relation: payload.patient?.relation || "SELF",
        isGuardian: !!payload.patient?.isGuardian,
      },
    });
    return {
      token: r.accessToken,
      customer: {
        id: str(r.customerId),
        phone: r.phone || payload.phone,
        name: payload.patient?.fullName || r.fullName || null,
      },
    };
  },

  /* ---------- Danh mục công khai ---------- */
  async services() {
    return list(await get("/api/v1/public/services")).map(mapService);
  },

  async doctors() {
    return list(await get("/api/v1/public/doctors")).map(mapDoctor);
  },

  async slots({ date, serviceIds, doctorId, phone, patientFullName, birthYear, isNewPatient, asStaff }) {
    const data = await get(
      "/api/v1/public/appointments/available-slots",
      {
        date,
        serviceIds: serviceIdsOf(serviceIds).join(","),
        ...(num(doctorId) !== undefined ? { doctorId: num(doctorId) } : {}),
        ...(phone ? { phone } : {}),
        ...(patientFullName ? { patientFullName } : {}),
        ...(birthYear ? { birthYear } : {}),
        ...(isNewPatient ? { isNewPatient: true } : {}),
      },
      asStaff ? { auth: "staff" } : {},
    );
    return mapSlots(data);
  },

  /* ---------- Khách hàng ---------- */
  async createAppointment(payload) {
    const r = await post("/api/v1/customer/appointments", {
      bookingFor: payload.patient?.relation ? "RELATIVE" : "SELF",
      patientInfo: patientInfoOf(payload.patient),
      staffId: num(payload.doctorId),
      dateTime: payload.start,
      serviceIds: serviceIdsOf(payload.serviceIds),
      note: payload.patient?.note || undefined,
    });
    return { ...mapAppointment(r), notifyStatus: "SENT" };
  },

  async myAppointments({ scope }) {
    const tab = scope === "past" ? "PAST" : "UPCOMING";
    const data = await get("/api/v1/customer/appointments", { tab });
    return { scope, items: list(data).map(mapAppointment) };
  },

  appointmentIcs(id) {
    return get(`/api/v1/customer/appointments/${id}/ics`);
  },

  async reschedule(id, payload) {
    const r = await put(`/api/v1/customer/appointments/${id}/reschedule`, {
      dateTime: payload.start,
      staffId: num(payload.doctorId),
      note: payload.note || undefined,
    });
    return mapAppointment(r);
  },

  async cancel(id, reason) {
    const r = await patch(`/api/v1/customer/appointments/${id}/cancel`, { cancelReason: reason });
    return mapAppointment(r);
  },

  /* ---------- Lễ tân ---------- */
  async staffAppointments(params = {}) {
    const data = await get("/api/v1/staff/appointments", {
      ...(params.date ? { date: params.date } : {}),
      ...(params.status ? { status: params.status } : {}),
      ...(num(params.doctorId) !== undefined ? { doctorId: num(params.doctorId) } : {}),
    });
    return { items: list(data).map(mapAppointment) };
  },

  async staffCreate(payload) {
    const r = await post("/api/v1/staff/appointments", {
      source: staffSourceOf(payload.source),
      phone: payload.phone,
      patientInfo: patientInfoOf(payload.patient),
      staffId: num(payload.doctorId),
      dateTime: payload.start,
      serviceIds: serviceIdsOf(payload.serviceIds),
      isPriority: !!payload.isPriority,
      note: payload.note || undefined,
    });
    return mapAppointment(r);
  },

  async staffConfirm(id, payload = {}) {
    const r = await patch(`/api/v1/staff/appointments/${id}/confirm`, {
      ...(num(payload.doctorId) !== undefined ? { staffId: num(payload.doctorId) } : {}),
      // Ghế GH1..GH4 là dữ liệu mock; backend tự gán/giữ ghế khi không truyền chairId.
      ...(num(payload.chairId) !== undefined ? { chairId: num(payload.chairId) } : {}),
    });
    return mapAppointment(r);
  },

  async staffReschedule(id, payload) {
    const r = await put(`/api/v1/staff/appointments/${id}/reschedule`, {
      dateTime: payload.start,
      staffId: num(payload.doctorId),
      ...(num(payload.chairId) !== undefined ? { chairId: num(payload.chairId) } : {}),
      note: payload.note || undefined,
    });
    return mapAppointment(r);
  },

  async staffCancel(id, cancelReason) {
    const r = await patch(`/api/v1/staff/appointments/${id}/cancel`, { cancelReason });
    return mapAppointment(r);
  },

  async checkIn(id) {
    return mapAppointment(await patch(`/api/v1/staff/appointments/${id}/check-in`));
  },

  async noShow(id) {
    return mapAppointment(await patch(`/api/v1/staff/appointments/${id}/no-show`));
  },

  async reverseNoShow(id, reversedReason) {
    const r = await post(`/api/v1/staff/appointments/${id}/reverse-no-show`, { reversedReason });
    return mapAppointment(r);
  },

  /** L3 cần danh sách bác sĩ nghỉ trong ngày → backend có /staff/leaves (trạng thái APPROVED). */
  async schedules(date) {
    const data = await get("/api/v1/staff/leaves", {
      status: "APPROVED",
      ...(date ? { fromDate: date, toDate: date } : {}),
    });
    const leaves = list(data).map((l) => ({
      doctorId: str(l.staffId),
      doctorName: l.doctorName,
      date: l.leaveDate,
      start: l.startTime,
      end: l.endTime,
      reason: l.reason,
    }));
    return { date, leaves };
  },
};
