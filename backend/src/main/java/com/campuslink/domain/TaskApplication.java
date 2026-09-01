package com.campuslink.domain;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("task_application") public class TaskApplication { @TableId(type=IdType.AUTO) private Long id; private Long taskId; private Long applicantId; private String message; private LocalDateTime expectedFinishTime; private String status; private LocalDateTime createdAt; }
