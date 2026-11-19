package com.chia.dental.care.support;

import com.chia.dental.common.config.ClinicConfigService;
import com.chia.dental.repository.ClinicConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tham số nghiệp vụ CSKH đọc từ bảng clinic_config (FR-35).
 * Dùng lại {@link ClinicConfigService} của phân hệ đặt lịch cho số nguyên và giờ (để cùng một cách đọc,
 * cùng giá trị mặc định); chỉ bổ sung kiểu chuỗi và danh sách mà CSKH cần.
 */
@Service
@RequiredArgsConstructor
public class CareSettings {

    private final ClinicConfigRepository repository;
    private final ClinicConfigService base;
    private final ObjectMapper mapper;

    public int getInt(String key, int def) {
        return base.getIntConfig(key, def);
    }

    public LocalTime getTime(String key, LocalTime def) {
        return base.getTimeConfig(key, def);
    }

    public String getString(String key, String def) {
        return repository.findRawTextValueByKey(key).map(String::trim).filter(s -> !s.isEmpty()).orElse(def);
    }

    public List<Integer> getIntList(String key, List<Integer> def) {
        List<Integer> out = new ArrayList<>();
        array(key).ifPresent(n -> n.forEach(e -> {
            if (e.isNumber()) out.add(e.asInt());
        }));
        return out.isEmpty() ? def : out;
    }

    public List<String> getStringList(String key, List<String> def) {
        List<String> out = new ArrayList<>();
        array(key).ifPresent(n -> n.forEach(e -> {
            if (e.isTextual() && !e.asText().isBlank()) out.add(e.asText());
        }));
        return out.isEmpty() ? def : out;
    }

    private Optional<JsonNode> array(String key) {
        return repository.findRawTextValueByKey(key).flatMap(raw -> {
            try {
                JsonNode node = mapper.readTree(raw);
                return node.isArray() ? Optional.of(node) : Optional.<JsonNode>empty();
            } catch (JsonProcessingException e) {
                return Optional.<JsonNode>empty();
            }
        });
    }
}
