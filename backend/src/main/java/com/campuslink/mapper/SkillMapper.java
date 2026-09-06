package com.campuslink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuslink.domain.Skill;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SkillMapper extends BaseMapper<Skill> {
    @Select("SELECT * FROM skill WHERE id=#{id} FOR UPDATE")
    Skill lockById(@Param("id") Long id);
}
