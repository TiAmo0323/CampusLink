package com.campuslink.domain;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("report") public class Report { @TableId(type=IdType.AUTO) private Long id; private Long reporterId; private String targetType; private Long targetId; private String reasonType; private String description; private String previousStatus; private String status; private Long handlerId; private String handleResult; private LocalDateTime createdAt; private LocalDateTime handledAt; }
