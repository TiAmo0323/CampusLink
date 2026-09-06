package com.campuslink.domain;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("skill_exchange") public class SkillExchange { private LocalDateTime providerCompletedAt; @TableId(type=IdType.AUTO) private Long id; private Long requesterId; private Long providerId; private Long requestSkillId; private Long exchangeSkillId; private String message; private LocalDateTime scheduledTime; private String status; private LocalDateTime createdAt; private LocalDateTime completedAt; }
