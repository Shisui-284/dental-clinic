/** Khung giao diện desktop 1440 cho 2 cổng (5.2 §6). */
import { esc } from "./ui.js";
import { config } from "../config.js";
import { session } from "./session.js";

export const STAFF_NAV = [
  { group: "Đặt lịch & Điều phối", roles: ["RECEPTIONIST", "MANAGER"] },
  { id: "l1", code: "L1", label: "Duyệt lịch chờ", roles: ["RECEPTIONIST", "MANAGER"] },
  { id: "l2", code: "L2", label: "Tạo lịch hộ", roles: ["RECEPTIONIST", "MANAGER"] },
  { id: "l3", code: "L3", label: "Điều phối trong ngày", roles: ["RECEPTIONIST", "MANAGER"] },
  { group: "Cổng Bác sĩ", roles: ["DOCTOR", "MANAGER"] },
  { id: "b1", code: "B1", label: "Lịch khám & Xin nghỉ", roles: ["DOCTOR", "MANAGER"] },
  { group: "Quản trị phòng khám", roles: ["MANAGER"] },
  { id: "q1", code: "Q1", label: "Duyệt nghỉ & Xung đột", roles: ["MANAGER"] },
  { id: "q2", code: "Q2", label: "Quản lý Blacklist", roles: ["MANAGER"] },
  { group: "Ngoài phạm vi 7.2", roles: ["RECEPTIONIST", "MANAGER"] },
  { id: null, code: "L4", label: "Hồ sơ bệnh nhân", roles: ["RECEPTIONIST", "MANAGER"] },
  { id: null, code: "L5", label: "Công việc CSKH", roles: ["RECEPTIONIST", "MANAGER"] },
  { id: null, code: "L6", label: "Phản hồi & khiếu nại", roles: ["RECEPTIONIST", "MANAGER"] },
];

export function customerFrame({ content, activeStep = 0, phone = "" }) {
  const steps = ["Số điện thoại", "Người khám", "Dịch vụ & bác sĩ", "Ngày giờ", "Xác thực OTP", "Hoàn tất"];
  const stepBar = activeStep > 0
    ? `<div class="steps">${steps.map((s, i) => `<div class="step ${i + 1 === activeStep ? "on" : i + 1 < activeStep ? "done" : ""}">${i + 1}. ${esc(s)}</div>`).join("")}</div>`
    : "";
  return `
    <header class="cust-top">
      <div class="inner">
        <a class="brand" href="#/k1" style="text-decoration:none">
          <span class="mark">CH</span>
          <span>${esc(config.clinicName)}<small>Đặt lịch khám trực tuyến</small></span>
        </a>
        <div class="spacer"></div>
        <a class="btn sm" href="#/k7">Lịch hẹn của tôi</a>
        <a class="btn sm ghost" href="#/login">Cổng nhân sự</a>
        <span class="small muted">Hotline ${esc(config.hotline)}</span>
      </div>
    </header>
    <main class="cust-main">
      ${phone ? `<div class="banner info">Đang đặt lịch cho số <b>${esc(phone)}</b>. Bạn sẽ nhận OTP ở bước cuối.</div>` : ""}
      ${stepBar}
      ${content}
    </main>`;
}

export function staffFrame({ content, active }) {
  const st = session.staff || {};
  const role = st.role || "RECEPTIONIST";
  const nav = STAFF_NAV
    .filter((n) => !n.roles || n.roles.includes(role))
    .map((n) => {
      if (n.group) return `<div class="group">${esc(n.group)}</div>`;
      if (!n.id) return `<a href="#" class="disabled" aria-disabled="true" style="opacity:.45;pointer-events:none">${esc(n.label)}<span class="k">${n.code}</span></a>`;
      return `<a href="#/${n.id}" class="${active === n.id ? "active" : ""}">${esc(n.label)}<span class="k">${n.code}</span></a>`;
    }).join("");
  return `
    <div class="staff-shell">
      <aside class="sidebar">
        <div class="brand"><span class="mark">CH</span><span>${esc(config.clinicName)}<small>Cổng nhân sự</small></span></div>
        <nav class="nav">${nav}</nav>
        <div class="side-foot">Phân hệ Đặt lịch (7.1 + 7.2)<br>Desktop 1440×900</div>
      </aside>
      <div class="staff-body">
        <div class="topbar">
          <span class="title" id="topbar-title"></span>
          <div class="spacer"></div>
          <a class="btn sm" href="#/k1">Cổng khách hàng</a>
          <div class="row" style="gap:8px">
            <div class="avatar">${esc((st.name || "?").split(" ").slice(-1)[0]?.[0] || "?")}</div>
            <div>
              <div style="font-weight:600">${esc(st.name || "Chưa đăng nhập")}</div>
              <div class="who">${esc(st.role === "MANAGER" ? "Quản lý" : st.role === "DOCTOR" ? "Bác sĩ" : "Lễ tân")}</div>
            </div>
          </div>
          <button class="btn sm" data-logout>Đăng xuất</button>
        </div>
        <main class="staff-main">${content}</main>
      </div>
    </div>`;
}
