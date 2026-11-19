package com.chia.dental.care.support;

import com.chia.dental.care.CareConstants;
import com.chia.dental.common.exception.BusinessException;
import com.chia.dental.entity.StaffAccount;
import com.chia.dental.repository.StaffAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Chia việc vòng tròn giữa các lễ tân đang hoạt động (quyết định chốt). Không có lễ tân -> dự phòng Quản lý.
 * Gọi trong transaction: advisory lock theo transaction đảm bảo hai việc sinh đồng thời không trùng người.
 * (Hệ thống chưa có dữ liệu ca trực của lễ tân nên "đang hoạt động" = tài khoản status TRUE.)
 */
@Component
@RequiredArgsConstructor
public class AssigneeSelector {

    private static final long LOCK_TASK = 70_001L;
    private static final long LOCK_COMPLAINT = 70_002L;

    private final StaffAccountRepository staffRepository;
    private final JdbcTemplate jdbc;

    public long nextForTask() {
        return next(LOCK_TASK,
                "SELECT assignee_id FROM care_task WHERE source = 'SYSTEM' ORDER BY task_id DESC LIMIT 1");
    }

    public long nextForComplaint() {
        return next(LOCK_COMPLAINT,
                "SELECT handler_id FROM complaint WHERE handler_id IS NOT NULL ORDER BY complaint_id DESC LIMIT 1");
    }

    private long next(long lockKey, String lastSql) {
        jdbc.execute("SELECT pg_advisory_xact_lock(" + lockKey + ")");
        List<Long> pool = staffRepository.findByRoleRoleNameAndStatusTrue(CareConstants.ROLE_RECEPTIONIST).stream()
                .map(StaffAccount::getStaffId).sorted(Comparator.naturalOrder()).toList();
        if (pool.isEmpty()) {
            pool = staffRepository.findByRoleRoleNameAndStatusTrue(CareConstants.ROLE_MANAGER).stream()
                    .map(StaffAccount::getStaffId).sorted(Comparator.naturalOrder()).toList();
        }
        if (pool.isEmpty()) {
            throw new BusinessException("NO_ASSIGNEE", "Không có lễ tân hoặc quản lý đang hoạt động để giao việc CSKH.",
                    HttpStatus.CONFLICT);
        }
        List<Long> last = jdbc.queryForList(lastSql, Long.class);
        if (last.isEmpty()) return pool.get(0);
        long prev = last.get(0);
        return pool.stream().filter(id -> id > prev).findFirst().orElse(pool.get(0));
    }
}
