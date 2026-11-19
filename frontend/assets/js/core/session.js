/** Phiên làm việc: khách (phone + JWT), nhân sự (JWT + role), draft đặt lịch. */
const SKEY = "qldapm_session_72";

function load() {
  try {
    const raw = sessionStorage.getItem(SKEY);
    if (raw) return JSON.parse(raw);
  } catch { /* ignore */ }
  return { customer: null, staff: null, draft: null };
}

const state = load();

function save() {
  try { sessionStorage.setItem(SKEY, JSON.stringify(state)); } catch { /* ignore */ }
}

export const session = state;

export function getToken() {
  return state.staff?.token || state.customer?.token || null;
}

export function setStaffLogin(data) {
  state.staff = { token: data.token, ...data.staff };
  state.customer = null;
  save();
}

export function setStaffLogout() {
  state.staff = null;
  save();
}

export function setCustomerPhone(phone) {
  // Giữ nguyên token/name nếu đã xác thực OTP — chỉ cập nhật số điện thoại.
  state.customer = { ...(state.customer || {}), phone };
  save();
}

export function setCustomerAuth(data) {
  state.customer = { ...(state.customer || {}), token: data.token, ...data.customer };
  save();
}

export function customerLogout() {
  state.customer = null;
  save();
}

export function hasStaffRole(...roles) {
  return !!state.staff && (!roles.length || roles.includes(state.staff.role));
}

export function setDraft(patch) {
  state.draft = { ...(state.draft || {}), ...patch };
  save();
}

export function getDraft() {
  return state.draft || {};
}

export function clearDraft() {
  state.draft = null;
  save();
}
