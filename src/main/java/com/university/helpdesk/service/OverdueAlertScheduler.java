package com.university.helpdesk.service;
import com.university.helpdesk.entity.TicketStatus;
import com.university.helpdesk.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.List;
@Component
public class OverdueAlertScheduler {
    private final TicketRepository tickets;private final OverdueAlertService alerts;private final boolean enabled;
    public OverdueAlertScheduler(TicketRepository tickets,OverdueAlertService alerts,@Value("${app.sla.alerts.enabled:true}") boolean enabled){this.tickets=tickets;this.alerts=alerts;this.enabled=enabled;}
    @Scheduled(initialDelayString="${app.sla.alerts.initial-delay:60000}",fixedDelayString="${app.sla.alerts.delay:300000}")
    public void check(){
        if(!enabled)return;
        LocalDateTime now=LocalDateTime.now();int page=0;
        org.springframework.data.domain.Page<com.university.helpdesk.entity.Ticket> result;
        do{result=tickets.findByStatusInOrderByTicketIdAsc(List.of(TicketStatus.NEW,TicketStatus.ASSIGNED,TicketStatus.IN_PROGRESS,TicketStatus.ESCALATED),PageRequest.of(page++,100));
            for(var ticket:result)try{alerts.alert(ticket.getTicketId(),now);}catch(RuntimeException ex){LoggerFactory.getLogger(getClass()).warn("Could not process overdue alert for ticket {}",ticket.getTicketId(),ex);}
        }while(result.hasNext());
    }
}
