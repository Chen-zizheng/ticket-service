package com.czz.ticket.web;

import com.czz.ticket.domain.Assignee;
import com.czz.ticket.domain.Ticket;
import com.czz.ticket.dto.AssignRequest;
import com.czz.ticket.dto.CreateTicketRequest;
import com.czz.ticket.dto.PageResponse;
import com.czz.ticket.dto.StatsResponse;
import com.czz.ticket.dto.StatusChangeRequest;
import com.czz.ticket.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public Ticket create(@Valid @RequestBody CreateTicketRequest request) {
        return ticketService.create(request);
    }

    @GetMapping("/tickets/{id}")
    public Ticket get(@PathVariable Long id) {
        return ticketService.get(id);
    }

    @GetMapping("/tickets")
    public PageResponse<Ticket> list(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) String priority,
                                     @RequestParam(required = false) Long assigneeId,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return ticketService.page(status, priority, assigneeId, page, size);
    }

    @PostMapping("/tickets/{id}/assign")
    public Ticket assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request) {
        return ticketService.assign(id, request);
    }

    @PostMapping("/tickets/{id}/status")
    public Ticket changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request) {
        return ticketService.changeStatus(id, request.status());
    }

    @GetMapping("/stats")
    public StatsResponse stats() {
        return ticketService.stats();
    }

    @GetMapping("/assignees")
    public List<Assignee> assignees() {
        return ticketService.assignees();
    }
}
