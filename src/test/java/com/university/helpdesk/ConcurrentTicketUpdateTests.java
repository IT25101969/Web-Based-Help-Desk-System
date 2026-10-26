package com.university.helpdesk;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:concurrent-ticket-review;MODE=MySQL;DB_CLOSE_DELAY=-1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConcurrentTicketUpdateTests extends TestAccounts {
    @Autowired TicketService service;
    @Autowired TicketRepository tickets;
    @Autowired CategoryRepository categories;
    @Autowired PlatformTransactionManager transactions;

    @Test void priorityEditPreservesAConcurrentStatusChange() throws Exception {
        var transaction = new TransactionTemplate(transactions);
        Long id = transaction.execute(status -> {
            var category = new Category(); category.setCategoryName("Concurrent update review");
            categories.saveAndFlush(category);
            return service.createTicket("STU001", category.getCategoryId(), TicketType.INCIDENT,
                    "Concurrent edit", "Verify status and priority persist together", "Network", "High").getTicketId();
        });
        var statusWritten = new CountDownLatch(1);
        var releaseStatus = new CountDownLatch(1);
        var priorityStarted = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Future<?> statusUpdate = workers.submit(() -> transaction.executeWithoutResult(status -> {
                service.changeStatus(id, TicketStatus.IN_PROGRESS, "ADM001", "Start work");
                tickets.flush(); statusWritten.countDown();
                try { assertTrue(releaseStatus.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new RuntimeException(ex); }
            }));
            assertTrue(statusWritten.await(10, TimeUnit.SECONDS));
            Future<?> priorityUpdate = workers.submit(() -> {
                priorityStarted.countDown();
                service.updatePriority(id, TicketPriority.CRITICAL, "ADM001");
            });
            try {
                assertTrue(priorityStarted.await(10, TimeUnit.SECONDS));
                // Give the competing transaction time to reach the held database row.
                assertThrows(TimeoutException.class, () -> priorityUpdate.get(500, TimeUnit.MILLISECONDS));
            } finally { releaseStatus.countDown(); }
            statusUpdate.get(10, TimeUnit.SECONDS);
            priorityUpdate.get(10, TimeUnit.SECONDS);
            Ticket stored = tickets.findById(id).orElseThrow();
            assertEquals(TicketStatus.IN_PROGRESS, stored.getStatus(), "Priority editing must preserve the committed status");
            assertEquals(TicketPriority.CRITICAL, stored.getPriority());
        } finally { releaseStatus.countDown(); }
    }
}
