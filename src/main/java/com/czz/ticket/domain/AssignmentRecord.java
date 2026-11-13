package com.czz.ticket.domain;

import java.time.LocalDateTime;

public class AssignmentRecord {

    public Long id;
    public Long ticketId;
    public Long assigneeId;
    public String note;
    public LocalDateTime createdAt;
}
