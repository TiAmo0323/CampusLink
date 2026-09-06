package com.campuslink.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_assignment_history")
public class OrderAssignmentHistory {
    @TableId(type=IdType.AUTO) private Long id;
    private Long orderId;
    private Long previousAccepterId;
    private Long newAccepterId;
    private Integer previousRound;
    private LocalDateTime previousStartedAt;
    private LocalDateTime previousDueAt;
    private String reason;
    private LocalDateTime createdAt;
}
