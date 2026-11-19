package com.chia.dental.care.feedback;

import com.chia.dental.care.support.CareSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Phát hiện dấu hiệu bất thường trong bình luận (SRS K9: "đau kéo dài", "chảy máu nhiều").
 * So khớp không phân biệt hoa thường và KHÔNG phụ thuộc dấu tiếng Việt (khách hay gõ không dấu).
 * Danh sách từ khóa lấy từ clinic_config.alert_keywords (Quản lý chỉnh được).
 */
@Component
@RequiredArgsConstructor
public class SymptomDetector {

    static final List<String> DEFAULT_KEYWORDS = List.of(
            "đau kéo dài", "đau nhức kéo dài", "đau nhiều", "chảy máu nhiều", "máu chảy nhiều");

    private final CareSettings config;

    public boolean detect(String comment) {
        return detect(comment, config.getStringList("alert_keywords", DEFAULT_KEYWORDS));
    }

    public static boolean detect(String comment, List<String> keywords) {
        if (comment == null || comment.isBlank()) return false;
        String text = normalize(comment);
        for (String k : keywords) {
            String nk = normalize(k);
            if (!nk.isEmpty() && text.contains(nk)) return true;
        }
        return false;
    }

    /** Hạ chữ thường, bỏ dấu, đ -> d, gộp khoảng trắng. */
    public static String normalize(String s) {
        String lower = s.toLowerCase(Locale.ROOT).replace('đ', 'd');
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return decomposed.replaceAll("\\s+", " ").trim();
    }
}
