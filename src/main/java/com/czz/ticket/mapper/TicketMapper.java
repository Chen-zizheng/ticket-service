package com.czz.ticket.mapper;

import com.czz.ticket.domain.Ticket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TicketMapper {

    int insert(Ticket ticket);

    Ticket findById(@Param("id") Long id);

    List<Ticket> findPage(@Param("status") String status,
                          @Param("priority") String priority,
                          @Param("assigneeId") Long assigneeId,
                          @Param("offset") int offset,
                          @Param("size") int size);

    long count(@Param("status") String status,
               @Param("priority") String priority,
               @Param("assigneeId") Long assigneeId);

    /** 乐观锁派单：只有版本号与来源状态都匹配才更新成功，返回影响行数。 */
    int assign(@Param("id") Long id,
               @Param("assigneeId") Long assigneeId,
               @Param("expectedVersion") int expectedVersion,
               @Param("fromStatus") String fromStatus,
               @Param("now") LocalDateTime now);

    int changeStatus(@Param("id") Long id,
                     @Param("expectedVersion") int expectedVersion,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus,
                     @Param("now") LocalDateTime now,
                     @Param("resolvedAt") LocalDateTime resolvedAt);

    int insertAssignment(@Param("ticketId") Long ticketId,
                         @Param("assigneeId") Long assigneeId,
                         @Param("note") String note,
                         @Param("now") LocalDateTime now);

    List<Ticket> countGroupByStatus();

    List<Ticket> countGroupByPriority();

    List<Ticket> countGroupByAssignee();
}
