package com.campuslink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuslink.domain.CampusTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskMapper extends BaseMapper<CampusTask> {
    @Select("SELECT * FROM task WHERE id=#{id} FOR UPDATE")
    CampusTask lockById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM task WHERE is_delisted=FALSE AND status=#{status} AND application_deadline>#{now}")
    long countVisibleRecruiting(@Param("status") String status,@Param("now") LocalDateTime now);

    @Select("SELECT category AS name, COUNT(*) AS count_value FROM task GROUP BY category ORDER BY category")
    List<MapperRows.CategoryCount> categoryCounts();
}
