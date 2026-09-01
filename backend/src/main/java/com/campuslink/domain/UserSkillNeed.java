package com.campuslink.domain;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("user_skill_need") public class UserSkillNeed { @TableId(type=IdType.AUTO) private Long id; private Long userId; private Long skillId; private Integer priority; private String description; private String preferredMode; private LocalDateTime createdAt; }
