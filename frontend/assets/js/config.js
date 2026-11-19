/**
 * Cấu hình runtime cho frontend 7.2.
 * - mode = "mock": dùng MockAdapter (dữ liệu mẫu, không cần backend) — mặc định để demo.
 * - mode = "live": gọi REST API thật theo 5.3 tại apiBase.
 * Đổi trong UI: nút "Cấu hình API" ở góc phải topbar (lưu localStorage).
 */
const KEY = "qldapm_frontend_72";

const DEFAULTS = {
  mode: "mock",
  apiBase: "http://localhost:8080",
  clinicName: "Phòng khám nha khoa CHIA",
  hotline: "1900-6868",
};

function read() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return { ...DEFAULTS };
    const saved = JSON.parse(raw);
    return { ...DEFAULTS, ...saved };
  } catch {
    return { ...DEFAULTS };
  }
}

export const config = read();

/**
 * Ghi đè tạm thời bằng query string (không lưu localStorage) — tiện khi nối thử backend 7.1:
 *   http://localhost:5173/?mode=live&api=http://localhost:8080#/k1
 * Bỏ query string là quay lại cấu hình đã lưu.
 */
try {
  const q = new URLSearchParams(location.search);
  const api = q.get("api");
  const mode = q.get("mode");
  if (api) config.apiBase = api;
  if (mode === "live" || mode === "mock") config.mode = mode;
} catch { /* ignore */ }

export function updateConfig(patch) {
  Object.assign(config, patch);
  try {
    localStorage.setItem(KEY, JSON.stringify(config));
  } catch {
    /* ignore */
  }
}

export const isMock = () => config.mode !== "live";
export const hotline = () => config.hotline;
