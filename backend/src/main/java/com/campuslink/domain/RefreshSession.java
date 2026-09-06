package com.campuslink.domain;
import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;
@Data @TableName("refresh_session") public class RefreshSession {
 @TableId(type=IdType.AUTO) private Long id; private Long userId;private String tokenHash;private LocalDateTime expiresAt;private LocalDateTime createdAt;
}
