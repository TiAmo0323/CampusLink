package com.campuslink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuslink.domain.Review;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface ReviewMapper extends BaseMapper<Review> {
    @Select({"<script>", "SELECT reviewee_id AS id, AVG(rating) AS metric_value FROM review WHERE reviewee_id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "GROUP BY reviewee_id", "</script>"})
    List<MapperRows.AggregateValue> averageRatingsByIds(@Param("ids") Collection<Long> ids);

    @Select("SELECT reviewee_id AS id, AVG(rating) AS metric_value FROM review GROUP BY reviewee_id")
    List<MapperRows.AggregateValue> averageRatings();
}
