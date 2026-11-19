package com.chia.dental.care;

import com.chia.dental.care.feedback.SymptomDetector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymptomDetectorTest {

    private static final List<String> KW = List.of("đau kéo dài", "chảy máu nhiều");

    @Test
    void detectsKeywordWithDiacritics() {
        assertTrue(SymptomDetector.detect("Sau nhổ răng tôi bị đau kéo dài 2 ngày", KW));
    }

    @Test
    void detectsKeywordWithoutDiacriticsAndDifferentCase() {
        assertTrue(SymptomDetector.detect("CHAY MAU NHIEU qua", KW));
        assertTrue(SymptomDetector.detect("dau   keo dai", KW));
    }

    @Test
    void ignoresNormalComments() {
        assertFalse(SymptomDetector.detect("Bác sĩ nhiệt tình, phòng khám sạch sẽ", KW));
        assertFalse(SymptomDetector.detect(null, KW));
        assertFalse(SymptomDetector.detect("   ", KW));
    }

    @Test
    void normalizeHandlesDStroke() {
        assertEquals("dau keo dai", SymptomDetector.normalize("Đau  kéo dài"));
    }
}
