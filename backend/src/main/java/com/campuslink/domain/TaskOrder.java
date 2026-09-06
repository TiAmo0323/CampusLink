package com.campuslink.domain;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("task_order") public class TaskOrder { private Integer assignmentRound=1; private LocalDateTime dueAt; @TableId(type=IdType.AUTO) private Long id; private Long taskId; private Long publisherId; private Long accepterId; private String status; @Version private Integer version; private LocalDateTime acceptedAt; private LocalDateTime startedAt; private LocalDateTime completedAt; private LocalDateTime updatedAt; }
