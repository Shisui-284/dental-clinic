package com.chia.dental.care.web;

import com.chia.dental.care.CareDtos.*;
import com.chia.dental.care.feedback.FeedbackService;
import com.chia.dental.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/feedback")
@RequiredArgsConstructor
@Tag(name = "12. CSKH - Đánh giá công khai (K9)",
        description = "Khách đánh giá qua link token dùng một lần, không cần đăng nhập")
public class PublicFeedbackController {

    private final FeedbackService service;

    @GetMapping("/{token}")
    @Operation(summary = "Thông tin hiển thị trên trang đánh giá (phòng khám, giờ khám, bác sĩ, dịch vụ); link hết hạn/đã dùng trả 410")
    public ApiResponse<FeedbackLinkInfo> info(@PathVariable String token) {
        return ApiResponse.ok(service.linkInfo(token));
    }

    @PostMapping("/{token}")
    @Operation(summary = "Gửi đánh giá 1–5 sao + bình luận ≤1.000 ký tự (≤3 sao hoặc có từ khóa triệu chứng sẽ bật cảnh báo đỏ)")
    public ApiResponse<SubmitFeedbackResponse> submit(@PathVariable String token,
                                                      @Valid @RequestBody SubmitFeedbackRequest req) {
        SubmitFeedbackResponse res = service.submit(token, req.rating(), req.comment());
        return ApiResponse.ok(res.message(), res);
    }
}
