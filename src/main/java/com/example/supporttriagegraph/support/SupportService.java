package com.example.supporttriagegraph.support;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Service
public class SupportService {

  private static final String HUMAN_CLASSIFY = "human-classify";

  private final CompiledGraph supportGraph;
  private final TicketStore ticketStore;

  public SupportService(CompiledGraph supportGraph, TicketStore ticketStore) {
    this.supportGraph = supportGraph;
    this.ticketStore = ticketStore;
  }

  public Ticket submit(String question) {
    String ticketId = UUID.randomUUID().toString();
    RunnableConfig config = RunnableConfig.builder().threadId(ticketId).build();

    OverAllState state = supportGraph
        .invoke(Map.of("user_question", question), config)
        .orElseThrow();

    Ticket ticket = new Ticket(ticketId, question);
    applyState(ticket, state);
    ticketStore.save(ticket);
    return ticket;
  }

  public Ticket resume(String ticketId, String category) throws Exception {
    if (!"billing".equals(category) && !"technical".equals(category)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "category must be 'billing' or 'technical'");
    }

    Ticket ticket = ticketStore.find(ticketId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "No ticket with id " + ticketId));

    if (ticket.getStatus() != TicketStatus.NEEDS_CLASSIFICATION) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "Ticket " + ticketId + " is not waiting for classification");
    }

    RunnableConfig resumeConfig = supportGraph
        .getState(RunnableConfig.builder().threadId(ticketId).build())
        .config()
        .withResume();

    resumeConfig = supportGraph.updateState(
        resumeConfig,
        Map.of("category", category),
        HUMAN_CLASSIFY);

    OverAllState state = supportGraph.invoke(Map.of(), resumeConfig).orElseThrow();

    applyState(ticket, state);
    ticketStore.save(ticket);
    return ticket;
  }

  public Ticket get(String ticketId) {
    return ticketStore.find(ticketId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "No ticket with id " + ticketId));
  }

  private void applyState(Ticket ticket, OverAllState state) {
    String category = state.value("category", "unknown");
    ticket.setCategory(category);
    ticket.setAttempts(state.value("attempts", 0));

    if ("unknown".equals(category)) {
      ticket.setStatus(TicketStatus.NEEDS_CLASSIFICATION);
    } else if (state.value("escalated", false)) {
      ticket.setStatus(TicketStatus.ESCALATED);
      ticket.setResponse(state.value("finalResponse", ""));
    } else {
      ticket.setStatus(TicketStatus.RESOLVED);
      ticket.setResponse(state.value("response", ""));
    }
  }

}
