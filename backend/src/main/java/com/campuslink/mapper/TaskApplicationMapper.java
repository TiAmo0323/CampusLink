package com.campuslink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuslink.domain.TaskApplication;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface TaskApplicationMapper extends BaseMapper<TaskApplication> {
    @Select({"<script>",
            "SELECT task_id AS id, COUNT(*) AS metric_value FROM task_application WHERE status&lt;&gt;#{withdrawn} AND task_id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "GROUP BY task_id", "</script>"})
    List<MapperRows.AggregateValue> countActiveByTaskIds(@Param("ids") Collection<Long> ids,
                                                         @Param("withdrawn") String withdrawn);

    @Select("""
            SELECT a.*
              FROM task_application a
              JOIN `user` u ON u.id = a.applicant_id
             WHERE a.task_id = #{taskId}
             ORDER BY u.credit_score DESC, a.id DESC
            """)
    Page<TaskApplication> selectApplicantPage(Page<TaskApplication> page,
                                              @Param("taskId") Long taskId);
}
