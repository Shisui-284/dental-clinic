/**
 * API service layer — ánh xạ 1-1 endpoint 5.3 dùng cho phân hệ Đặt lịch (7.2).
 * - mode "mock": gọi MockAdapter (localStorage), không cần backend.
 * - mode "live": gọi backend 7.1 qua LiveAdapter (api/live.js) — nơi duy nhất
 *   chuyển đổi shape giữa hợp đồng 5.3 và shape nội bộ của màn hình.
 */
import { get, post, put, patch } from "../core/http.js";
import { isMock } from "../config.js";
import { live } from "./live.js";

/** Chọn nhánh Mock hay Live tại thời điểm gọi (đổi mode không cần tải lại module). */
const route = (mockFn, liveFn) => (...args) => (isMock() ? mockFn(...args) : liveFn(...args));

export const api = {
  /* ---------- Auth / OTP ---------- */
  staffLogin: route(
    (username, password) => post("/api/v1/auth/staff/login", { username, password }),
    live.staffLogin,
  ),
  staffLogout: route(
    () => post("/api/v1/auth/staff/logout", {}),
    live.staffLogout,
  ),
  checkPhone: route(
    (phone) => post("/api/v1/auth/customer/check-phone", { phone }),
    live.checkPhone,
  ),
  requestOtp: route(
    (phone) => post("/api/v1/auth/otp/request", { phone }),
    live.requestOtp,
  ),
  verifyOtp: route(
    (payload) => post("/api/v1/auth/otp/verify", payload),
    live.verifyOtp,
  ),

  /* ---------- Danh mục công khai ---------- */
  services: route(() => get("/api/v1/public/services"), live.services),
  doctors: route(() => get("/api/v1/public/doctors"), live.doctors),
  slots: route(
    ({ date, serviceIds, doctorId }) =>
      get("/api/v1/public/appointments/available-slots", {
        date,
        serviceIds: (serviceIds || []).join(","),
        ...(doctorId ? { doctorId } : {}),
      }),
    live.slots,
  ),

  /* ---------- Khách hàng ---------- */
  createAppointment: route(
    (payload) => post("/api/v1/customer/appointments", payload),
    live.createAppointment,
  ),
  myAppointments: route(
    ({ phone, scope }) => get("/api/v1/customer/appointments", { phone, scope }),
    live.myAppointments,
  ),
  appointmentIcs: route(
    (id) => get(`/api/v1/customer/appointments/${id}/ics`),
    live.appointmentIcs,
  ),
  reschedule: route(
    (id, payload) => put(`/api/v1/customer/appointments/${id}/reschedule`, payload),
    live.reschedule,
  ),
  cancel: route(
    (id, reason) => patch(`/api/v1/customer/appointments/${id}/cancel`, { reason }),
    live.cancel,
  ),

  /* ---------- Lễ tân ---------- */
  staffAppointments: route(
    (params) => get("/api/v1/staff/appointments", params),
    live.staffAppointments,
  ),
  staffCreate: route(
    (payload) => post("/api/v1/staff/appointments", payload),
    live.staffCreate,
  ),
  staffConfirm: route(
    (id, payload) => patch(`/api/v1/staff/appointments/${id}/confirm`, payload),
    live.staffConfirm,
  ),
  staffReschedule: route(
    (id, payload) => put(`/api/v1/staff/appointments/${id}/reschedule`, payload),
    live.staffReschedule,
  ),
  staffCancel: route(
    (id, cancelReason) => patch(`/api/v1/staff/appointments/${id}/cancel`, { cancelReason }),
    live.staffCancel,
  ),
  checkIn: route(
    (id) => patch(`/api/v1/staff/appointments/${id}/check-in`, {}),
    live.checkIn,
  ),
  noShow: route(
    (id) => patch(`/api/v1/staff/appointments/${id}/no-show`, {}),
    live.noShow,
  ),
  reverseNoShow: route(
    (id, reversedReason) => post(`/api/v1/staff/appointments/${id}/reverse-no-show`, { reversedReason }),
    live.reverseNoShow,
  ),
  schedules: route(
    (date) => get("/api/v1/staff/schedules", { date }),
    live.schedules,
  ),
  staffChairs: route(
    () => get("/api/v1/staff/chairs"),
    live.staffChairs,
  ),
  doctorSchedules: route(
    (params) => get("/api/v1/staff/doctor-schedules", params),
    live.doctorSchedules,
  ),
  createSchedule: route(
    (payload) => post("/api/v1/staff/schedules", payload),
    live.createSchedule,
  ),
  doctorRequestLeave: route(
    (payload) => post("/api/v1/doctor/leaves", payload),
    live.doctorRequestLeave,
  ),
  staffLeaves: route(
    (params) => get("/api/v1/staff/leaves", params),
    live.staffLeaves,
  ),
  approveLeave: route(
    (id, decision) => patch(`/api/v1/manager/leaves/${id}/approval`, { decision }),
    live.approveLeave,
  ),
  leaveConflicts: route(
    (params) => get("/api/v1/staff/appointments/leave-conflicts", params),
    live.leaveConflicts,
  ),
  managerBlacklist: route(
    () => get("/api/v1/manager/blacklist"),
    live.managerBlacklist,
  ),
  unlockBlacklist: route(
    (customerId, reason) => post(`/api/v1/manager/blacklist/${customerId}/unlock`, { reason }),
    live.unlockBlacklist,
  ),
};
