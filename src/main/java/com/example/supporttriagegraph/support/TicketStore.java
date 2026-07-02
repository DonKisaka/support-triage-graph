package com.example.supporttriagegraph.support;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TicketStore {

  private final ConcurrentHashMap<String, Ticket> tickets = new ConcurrentHashMap<>();

  public void save(Ticket ticket) {
    tickets.put(ticket.getId(), ticket);
  }

  public Optional<Ticket> find(String id) {
    return Optional.ofNullable(tickets.get(id));
  }

}
