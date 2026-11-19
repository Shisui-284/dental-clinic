/** HTTP client: tự chọn MockAdapter hay REST API thật (config.mode). */
import { config, isMock } from "../config.js";
import { mockRequest } from "./mock.js";
import { session } from "./session.js";

export class ApiError extends Error {
  constructor(status, code, message, details) {
    super(message || "Đã có lỗi xảy ra.");
    this.name = "ApiError";
    this.status = status || 0;
    this.code = code || "UNKNOWN";
    this.details = details;
  }
}

const TIMEOUT_MS = 20000;

function buildUrl(path, query) {
  const base = String(config.apiBase || "").replace(/\/+$/, "");
  const url = base + path;
  const q = query instanceof URLSearchParams ? query : new URLSearchParams(query || {});
  const s = q.toString();
  return s ? `${url}?${s}` : url;
}

/** Token theo cổng: khách hàng dùng Customer JWT, nhân sự dùng Staff JWT (5.3 §a). */
function authToken(path, override) {
  if (override === "staff") return session.staff?.token || null;
  if (override === "customer") return session.customer?.token || null;
  if (path.startsWith("/api/v1/customer/") || path.startsWith("/api/v1/doctor/")) {
    return session.customer?.token || null;
  }
  if (path.startsWith("/api/v1/staff/") || path.startsWith("/api/v1/manager/")) {
    return session.staff?.token || null;
  }
  return null;
}

/** Bóc Response Envelope { success, code, message, data, fieldErrors } của backend 7.1. */
function unwrapEnvelope(payload) {
  if (
    payload &&
    typeof payload === "object" &&
    !Array.isArray(payload) &&
    Object.prototype.hasOwnProperty.call(payload, "success")
  ) {
    if (!payload.success) {
      throw new ApiError(200, payload.code, payload.message, payload.fieldErrors);
    }
    return payload.data;
  }
  return payload;
}

function errorMessage(status, payload) {
  const base = String(config.apiBase || "").replace(/\/+$/, "");
  if (status === 0) {
    return `Không kết nối được backend tại ${base}. Kiểm tra backend đã chạy và CORS cho origin này. Dữ liệu bạn nhập vẫn được giữ.`;
  }
  return payload?.message || `Yêu cầu không thành công (HTTP ${status}).`;
}

export async function request(method, path, opts = {}) {
  const { body, query, auth } = opts;
  if (isMock()) return mockRequest(method, path, { body, query });

  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS);
  let res;
  try {
    const token = authToken(path, auth);
    res = await fetch(buildUrl(path, query), {
      method,
      headers: {
        Accept: "application/json",
        ...(body ? { "Content-Type": "application/json" } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
      signal: ctrl.signal,
    });
  } catch (e) {
    const aborted = e?.name === "AbortError";
    throw new ApiError(
      0,
      aborted ? "TIMEOUT" : "NETWORK",
      aborted
        ? `Backend không phản hồi sau ${TIMEOUT_MS / 1000}s. Kiểm tra lại máy chủ 7.1.`
        : errorMessage(0, null),
      e,
    );
  } finally {
    clearTimeout(timer);
  }

  const isJson = (res.headers.get("content-type") || "").includes("application/json");
  const payload = isJson ? await res.json().catch(() => null) : await res.text();

  if (!res.ok) {
    const p = payload && typeof payload === "object" ? payload : {};
    throw new ApiError(res.status, p.code || `HTTP_${res.status}`, errorMessage(res.status, p), p);
  }
  return unwrapEnvelope(payload);
}

export const get = (path, query, opts = {}) => request("GET", path, { query, ...opts });
export const post = (path, body, query) => request("POST", path, { body, query });
export const put = (path, body) => request("PUT", path, { body });
export const patch = (path, body) => request("PATCH", path, { body });
