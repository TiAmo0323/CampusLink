package com.campuslink.domain;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("credit_record") public class CreditRecord { @TableId(type=IdType.AUTO) private Long id; private Long userId; private String businessType; private Long businessId; private Integer changeValue; private Integer beforeScore; private Integer afterScore; private String reason; private LocalDateTime createdAt; }
