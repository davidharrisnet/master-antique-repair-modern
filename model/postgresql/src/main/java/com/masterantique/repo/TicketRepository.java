package com.masterantique.repo;

import com.masterantique.model.Ticket;
import com.masterantique.model.TicketState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Integer> {

    long countByState(TicketState state);
}
