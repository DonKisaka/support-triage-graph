package com.example.supporttriagegraph.web;

import com.example.supporttriagegraph.support.Ticket;

public record TicketResponse(
    String ticketId,
    String status,
    String category,
    int attempts,
    String response) {

  public static TicketResponse from(Ticket ticket) {
    return new TicketResponse(
        ticket.getId(),
        ticket.getStatus().name(),
        ticket.getCategory(),
        ticket.getAttempts(),
        ticket.getResponse());
  }

}
