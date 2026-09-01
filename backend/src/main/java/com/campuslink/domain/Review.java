package com.campuslink.domain;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("review") public class Review { @TableId(type=IdType.AUTO) private Long id; private String businessType; private Long businessId; private Long reviewerId; private Long revieweeId; private Integer rating; private String content; private LocalDateTime createdAt; }
