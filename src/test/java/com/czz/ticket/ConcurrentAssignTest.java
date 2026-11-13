package com.czz.ticket;

import com.czz.ticket.dto.AssignRequest;
import com.czz.ticket.service.ConflictException;
import com.czz.ticket.service.TicketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发重复派单：32 个线程同时抢同一张工单，只能有一个成功。
 * 这条用真实事务，因此不能加 @Transactional（否则线程间看不到彼此的数据）。
 */
@SpringBootTest
class ConcurrentAssignTest {

    @Autowired
    private TicketService ticketService;

    @Test
    @DisplayName("32 线程抢派同一工单，恰好 1 次成功，其余 31 次拿到 409")
    void onlyOneAssignmentWins() throws Exception {
        var ticket = ticketService.create(new com.czz.ticket.dto.CreateTicketRequest("并发派单", "抢工单", "HIGH"));

        int attempts = 32;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(attempts);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        List<Throwable> unexpected = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            long assigneeId = (i % 3) + 1L;
            pool.submit(() -> {
                try {
                    start.await();
                    ticketService.assign(ticket.id, new AssignRequest(assigneeId, "并发"));
                    ok.incrementAndGet();
                } catch (ConflictException e) {
                    conflict.incrementAndGet();
                } catch (Throwable t) {
                    synchronized (unexpected) {
                        unexpected.add(t);
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(unexpected).isEmpty();
        assertThat(ok.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(attempts - 1);
        assertThat(ticketService.get(ticket.id).status).isEqualTo("ASSIGNED");
    }
}
