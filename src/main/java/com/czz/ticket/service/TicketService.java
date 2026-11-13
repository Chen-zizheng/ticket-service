package com.czz.ticket.service;

import com.czz.ticket.config.SqlCounter;
import com.czz.ticket.domain.Assignee;
import com.czz.ticket.domain.Priority;
import com.czz.ticket.domain.Ticket;
import com.czz.ticket.domain.TicketStatus;
import com.czz.ticket.dto.AssignRequest;
import com.czz.ticket.dto.CreateTicketRequest;
import com.czz.ticket.dto.PageResponse;
import com.czz.ticket.dto.StatsResponse;
import com.czz.ticket.mapper.AssigneeMapper;
import com.czz.ticket.mapper.TicketMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TicketService {

    /** 分页上限，防止 size=100000 把整张表打进内存。 */
    public static final int MAX_PAGE_SIZE = 100;

    private final TicketMapper ticketMapper;
    private final AssigneeMapper assigneeMapper;

    public TicketService(TicketMapper ticketMapper, AssigneeMapper assigneeMapper) {
        this.ticketMapper = ticketMapper;
        this.assigneeMapper = assigneeMapper;
    }

    @Transactional
    public Ticket create(CreateTicketRequest request) {
        Ticket ticket = new Ticket();
        ticket.title = request.title().trim();
        ticket.description = request.description();
        ticket.priority = Priority.parse(request.priority()).name();
        ticket.status = TicketStatus.OPEN.name();
        ticket.createdAt = LocalDateTime.now();
        ticket.updatedAt = ticket.createdAt;
        ticketMapper.insert(ticket);
        return ticketMapper.findById(ticket.id);
    }

    public Ticket get(Long id) {
        Ticket ticket = ticketMapper.findById(id);
        if (ticket == null) {
            throw new NotFoundException("工单不存在: " + id);
        }
        if (ticket.assigneeId != null) {
            Assignee assignee = assigneeMapper.findById(ticket.assigneeId);
            ticket.assigneeName = assignee == null ? null : assignee.name;
        }
        return ticket;
    }

    public PageResponse<Ticket> page(String status, String priority, Long assigneeId, int page, int size) {
        String normalizedStatus = status == null || status.isBlank() ? null : TicketStatus.parse(status).name();
        String normalizedPriority = priority == null || priority.isBlank() ? null : Priority.parse(priority).name();
        int pageNo = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        // 一条 SQL 拿本页数据，第二条 SQL 批量拿处理人；不会走到 1 + N
        List<Ticket> items = ticketMapper.findPage(normalizedStatus, normalizedPriority, assigneeId,
                (pageNo - 1) * pageSize, pageSize);
        fillAssigneeNames(items);
        long total = ticketMapper.count(normalizedStatus, normalizedPriority, assigneeId);
        return new PageResponse<>(items, total, pageNo, pageSize);
    }

    @Transactional
    public Ticket assign(Long id, AssignRequest request) {
        Assignee assignee = assigneeMapper.findById(request.assigneeId());
        if (assignee == null) {
            throw new NotFoundException("处理人不存在: " + request.assigneeId());
        }
        if (!assignee.active) {
            throw new ConflictException("处理人已停用: " + assignee.name);
        }

        Ticket current = ticketMapper.findById(id);
        if (current == null) {
            throw new NotFoundException("工单不存在: " + id);
        }
        TicketStatus from = TicketStatus.valueOf(current.status);
        if (from == TicketStatus.ASSIGNED || from == TicketStatus.IN_PROGRESS) {
            throw new ConflictException("工单已派给 " + current.assigneeId + "，不能重复派单");
        }
        if (!from.canMoveTo(TicketStatus.ASSIGNED)) {
            throw new ConflictException("状态 " + current.status + " 不能派单");
        }

        LocalDateTime now = LocalDateTime.now();
        int affected = ticketMapper.assign(id, assignee.id, current.version, current.status, now);
        if (affected == 0) {
            // 两个请求同时读到同一个 version，其中一个会在这里失败
            throw new ConflictException("工单已被其他人修改，请刷新后重试");
        }
        ticketMapper.insertAssignment(id, assignee.id, request.note(), now);
        Ticket updated = ticketMapper.findById(id);
        updated.assigneeName = assignee.name;
        return updated;
    }

    @Transactional
    public Ticket changeStatus(Long id, String rawStatus) {
        TicketStatus target = TicketStatus.parse(rawStatus);
        Ticket current = ticketMapper.findById(id);
        if (current == null) {
            throw new NotFoundException("工单不存在: " + id);
        }
        TicketStatus from = TicketStatus.valueOf(current.status);
        if (from == target) {
            throw new ConflictException("工单已经是 " + target.name() + " 状态");
        }
        if (!from.canMoveTo(target)) {
            throw new ConflictException("不允许从 " + from.name() + " 流转到 " + target.name());
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime resolvedAt = target == TicketStatus.RESOLVED ? now : current.resolvedAt;
        if (target != TicketStatus.RESOLVED && target != TicketStatus.CLOSED) {
            resolvedAt = null;
        }
        int affected = ticketMapper.changeStatus(id, current.version, current.status, target.name(), now, resolvedAt);
        if (affected == 0) {
            throw new ConflictException("工单已被其他人修改，请刷新后重试");
        }
        return get(id);
    }

    public StatsResponse stats() {
        // 三个 GROUP BY 查询，各自独立统计，避免把全表拉回内存
        List<StatsResponse.CountItem> byStatus = toItems(ticketMapper.countGroupByStatus());
        List<StatsResponse.CountItem> byPriority = toItems(ticketMapper.countGroupByPriority());
        List<StatsResponse.CountItem> byAssignee = toItems(ticketMapper.countGroupByAssignee());
        long total = byStatus.stream().mapToLong(StatsResponse.CountItem::count).sum();
        return new StatsResponse(total, byStatus, byPriority, byAssignee);
    }

    public List<Assignee> assignees() {
        return assigneeMapper.findAll();
    }

    private void fillAssigneeNames(List<Ticket> tickets) {
        Set<Long> ids = tickets.stream()
                .map(t -> t.assigneeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        List<Assignee> assignees = assigneeMapper.findByIds(new ArrayList<>(ids));
        Map<Long, String> nameById = new HashMap<>();
        for (Assignee a : assignees) {
            nameById.put(a.id, a.name);
        }
        for (Ticket t : tickets) {
            t.assigneeName = t.assigneeId == null ? null : nameById.get(t.assigneeId);
        }
    }

    private List<StatsResponse.CountItem> toItems(List<Ticket> rows) {
        return rows.stream()
                .map(t -> new StatsResponse.CountItem(t.status, t.version))
                .collect(Collectors.toList());
    }

    /** 供测试与调试使用：把当前库里的工单按 id 取出来做断言。 */
    public Map<Long, Ticket> loadAll() {
        return ticketMapper.findPage(null, null, null, 0, MAX_PAGE_SIZE)
                .stream()
                .collect(Collectors.toMap(t -> t.id, Function.identity()));
    }
}
