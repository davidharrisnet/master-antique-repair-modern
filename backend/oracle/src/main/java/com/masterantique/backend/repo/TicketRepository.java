package com.masterantique.backend.repo;

import com.masterantique.backend.model.Ticket;
import com.masterantique.backend.model.TicketState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Integer> {

    long countByState(TicketState state);
}
