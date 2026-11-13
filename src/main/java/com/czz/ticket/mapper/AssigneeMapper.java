package com.czz.ticket.mapper;

import com.czz.ticket.domain.Assignee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface AssigneeMapper {

    List<Assignee> findAll();

    Assignee findById(@Param("id") Long id);

    /** 批量取处理人，列表页因此只需要 2 条 SQL 而不是 N+1。 */
    List<Assignee> findByIds(@Param("ids") Collection<Long> ids);
}
