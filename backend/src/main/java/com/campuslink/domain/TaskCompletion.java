package com.campuslink.domain;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("task_completion") public class TaskCompletion { private Long submitterId; private Integer assignmentRound=1; @TableId(type=IdType.AUTO) private Long id; private Long orderId; private String description; private String proofUrl; private LocalDateTime submitTime; private String reviewStatus; private String rejectReason; }
