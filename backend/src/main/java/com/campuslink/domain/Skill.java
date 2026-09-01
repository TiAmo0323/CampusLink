package com.campuslink.domain;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("skill") public class Skill { @TableId(type=IdType.AUTO) private Long id; private String name; private String category; private String status; private LocalDateTime createdAt; }
