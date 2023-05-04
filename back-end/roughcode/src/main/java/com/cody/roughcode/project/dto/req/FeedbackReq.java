package com.cody.roughcode.project.dto.req;

import com.cody.roughcode.validation.NotZero;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;

@Getter
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class FeedbackReq {
    @Schema(description = "프로젝트 아이디", example = "1")
    @Range(min = -1, max = Integer.MAX_VALUE, message = "projectId 값이 범위를 벗어납니다")
    @NotZero(message = "projectId 값이 범위를 벗어납니다")
    Long projectId;

    @Schema(description = "피드백 내용", example = "개발새발 좋네요")
    @NotEmpty
    @Size(max = 500, message = "피드백은 500자를 넘을 수 없습니다")
    String content;
}
