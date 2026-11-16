package com.example.fridge_to_fork.messaging;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.fridge_to_fork.event.RemiEvent;

@Component
public class RemiEventRelay {
    
    private final EventPublisher eventPublisher;

    public RemiEventRelay(EventPublisher eventPublisher){
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void listen(RemiEvent event) {
        eventPublisher.publish(event);
    }
}
