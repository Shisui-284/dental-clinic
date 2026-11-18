package com.chia.dental.booking;

import com.chia.dental.entity.Appointment;
import com.chia.dental.entity.AppointmentServiceEntity;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class IcsCalendarService {

    private static final DateTimeFormatter ICS_UTC_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    public String buildAppointmentIcs(Appointment appt, List<AppointmentServiceEntity> services, String appointmentCode) {
        OffsetDateTime startUtc = appt.getDateTime().withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime end = appt.getEndTime() != null ? appt.getEndTime() : appt.getDateTime().plusMinutes(appt.getDuration());
        OffsetDateTime endUtc = end.withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);

        String serviceNames = services.stream()
                .map(s -> s.getService() != null ? s.getService().getName() : "Dịch vụ nha khoa")
                .collect(Collectors.joining(", "));

        String summary = "Lịch khám Nha khoa CHIA - " + (serviceNames.isBlank() ? appointmentCode : serviceNames);
        String description = String.format(
                "Mã lịch hẹn: %s\\nBệnh nhân: %s\\nBác sĩ phụ trách: %s\\nDịch vụ: %s\\nTrạng thái: %s",
                appointmentCode,
                escapeIcs(appt.getPatient().getFullName()),
                escapeIcs(appt.getStaff().getFullName()),
                escapeIcs(serviceNames),
                appt.getStatus()
        );

        return "BEGIN:VCALENDAR\r\n"
                + "VERSION:2.0\r\n"
                + "PRODID:-//CHIA Dental Clinic//Appointment System//VI\r\n"
                + "CALSCALE:GREGORIAN\r\n"
                + "METHOD:PUBLISH\r\n"
                + "BEGIN:VEVENT\r\n"
                + "UID:appointment-" + appt.getAppointmentId() + "@chia-dental.vn\r\n"
                + "DTSTAMP:" + nowUtc.format(ICS_UTC_FMT) + "\r\n"
                + "DTSTART:" + startUtc.format(ICS_UTC_FMT) + "\r\n"
                + "DTEND:" + endUtc.format(ICS_UTC_FMT) + "\r\n"
                + "SUMMARY:" + escapeIcs(summary) + "\r\n"
                + "DESCRIPTION:" + description + "\r\n"
                + "LOCATION:Phòng khám Nha khoa CHIA\r\n"
                + "STATUS:" + ("CANCELLED".equals(appt.getStatus()) ? "CANCELLED" : "CONFIRMED") + "\r\n"
                + "END:VEVENT\r\n"
                + "END:VCALENDAR\r\n";
    }

    private String escapeIcs(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\")
                .replace(",", "\\,")
                .replace(";", "\\;")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }
}

