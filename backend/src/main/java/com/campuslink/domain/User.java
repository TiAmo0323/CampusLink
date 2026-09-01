package com.campuslink.domain;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("`user`")
public class User { @TableId(type=IdType.AUTO) private Long id; private String username; private String passwordHash; private String nickname; private String studentNo; private String email; private String avatarUrl; private String bio; private String role; private Integer creditScore; private Integer availablePoints; private Integer frozenPoints; private String status; private LocalDateTime lastLoginAt; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
