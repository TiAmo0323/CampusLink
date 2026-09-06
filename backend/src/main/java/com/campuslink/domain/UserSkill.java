package com.campuslink.domain;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("user_skill") public class UserSkill { private String availableTime; @TableId(type=IdType.AUTO) private Long id; private Long userId; private Long skillId; private String proficiency; private String description; private String availableMode; private LocalDateTime createdAt; }
