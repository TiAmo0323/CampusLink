package com.campuslink.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public final class Requests {
    private Requests() {}
    public record Register(@NotBlank(message="用户名不能为空") @Size(min=3,max=32,message="用户名长度为3~32位") String username,
                           @NotBlank(message="密码不能为空") @Size(min=6,max=64,message="密码至少6位") String password,
                           @NotBlank(message="昵称不能为空") @Size(max=50) String nickname,
                           String studentNo, @Email(message="邮箱格式错误") String email) {}
    public record Login(@NotBlank(message="请输入用户名或邮箱") String account, @NotBlank(message="请输入密码") String password) {}
    public record Profile(@NotBlank(message="昵称不能为空") @Size(max=50) String nickname, @Size(max=255) String avatarUrl, @Size(max=255) String bio) {}
    public record TaskCreate(@NotBlank(message="标题不能为空") @Size(max=100) String title, @NotBlank(message="分类不能为空") String category,
                             @NotBlank(message="描述不能为空") String description, String location, @NotNull(message="请选择任务时间") LocalDateTime taskTime,
                             @NotNull(message="请选择申请截止时间") LocalDateTime applicationDeadline,
                             @NotNull @Min(value=1,message="奖励积分必须大于0") Integer rewardPoints,
                             @Min(0) @Max(100) Integer minCreditScore) {}
    public record TaskApply(@Size(max=255) String message, LocalDateTime expectedFinishTime) {}
    public record Completion(@NotBlank(message="完成说明不能为空") String description, @Size(max=255) String proofUrl) {}
    public record Reject(@NotBlank(message="请填写驳回原因") @Size(max=255) String reason) {}
    public record SkillOffer(@NotNull Long skillId, @NotBlank String proficiency, @Size(max=255) String description, String availableMode) {}
    public record SkillNeed(@NotNull Long skillId, @Min(1) @Max(5) Integer priority, @Size(max=255) String description, String preferredMode) {}
    public record Exchange(@NotNull Long providerId, @NotNull Long requestSkillId, Long exchangeSkillId, @Size(max=255) String message, LocalDateTime scheduledTime) {}
    public record ReviewCreate(@NotBlank String businessType, @NotNull Long businessId, @NotNull Long revieweeId, @Min(1) @Max(5) Integer rating, @Size(max=500) String content) {}
    public record ReportCreate(@NotBlank String targetType, @NotNull Long targetId, @NotBlank String reasonType, @Size(max=500) String description) {}
    public record ReportHandle(@NotBlank @Size(max=500) String result) {}
    public record AbnormalCreate(@NotBlank(message="请选择异常类型") @Pattern(regexp="NO_CONTACT|DISPUTE|TIMEOUT|OTHER",message="异常类型无效") String reasonType,
                                 @NotBlank(message="请填写异常说明") @Size(max=500) String description) {}
    public record AbnormalResolve(@NotBlank(message="请选择裁决结果") @Pattern(regexp="REFUND|SETTLE|RESUME",message="裁决结果无效") String decision,
                                  Long liableUserId, @Min(-20) @Max(20) Integer creditDelta,
                                  @NotBlank(message="请填写裁决说明") @Size(max=500) String result) {}
}
