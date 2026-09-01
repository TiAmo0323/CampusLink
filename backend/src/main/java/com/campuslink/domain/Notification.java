package com.campuslink.domain;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("notification") public class Notification { @TableId(type=IdType.AUTO) private Long id; private Long userId; private String type; private String title; private String content; private Long relatedBusinessId; private Boolean isRead; private LocalDateTime createdAt; }
