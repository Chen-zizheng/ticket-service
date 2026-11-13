package com.czz.ticket.domain;

import java.time.LocalDateTime;

/** 工单实体。字段直接暴露给 MyBatis 与 Jackson，避免为 10 个字段写 40 行样板访问器。 */
public class Ticket {

    public Long id;
    public String title;
    public String description;
    public String priority;
    public String status;
    public Long assigneeId;
    public int version;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public LocalDateTime resolvedAt;

    /** 列表视图里的处理人姓名，非表字段，由批量查询回填。 */
    public String assigneeName;
}
