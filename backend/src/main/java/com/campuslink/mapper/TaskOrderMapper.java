package com.campuslink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuslink.domain.TaskOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TaskOrderMapper extends BaseMapper<TaskOrder> {
    @Select("SELECT * FROM task_order WHERE id=#{id} FOR UPDATE")
    TaskOrder lockById(@Param("id") Long id);

    @Select({"<script>", "SELECT accepter_id AS id, COUNT(*) AS metric_value FROM task_order WHERE accepter_id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "AND status=#{status} GROUP BY accepter_id", "</script>"})
    List<MapperRows.AggregateValue> completedCountsByIds(@Param("ids") Collection<Long> ids,
                                                         @Param("status") String status);

    @Select("SELECT accepter_id AS id, COUNT(*) AS metric_value FROM task_order WHERE status=#{status} AND completed_at>=#{since} GROUP BY accepter_id")
    List<MapperRows.AggregateValue> completedCountsSince(@Param("status") String status,
                                                         @Param("since") LocalDateTime since);
}
