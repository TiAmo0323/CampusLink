package com.campuslink.domain;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("point_transaction") public class PointTransaction { private Integer beforeFrozen; private Integer afterFrozen; @TableId(type=IdType.AUTO) private Long id; private Long userId; private String businessType; private Long businessId; private Integer changeAmount; private Integer beforeBalance; private Integer afterBalance; private LocalDateTime createdAt; private String remark; }
