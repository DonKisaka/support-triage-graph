package com.example.supporttriagegraph.support;


public class Ticket {

  private final String id;
  private final String question;
  private TicketStatus status;
  private String category;
  private String response;
  private int attempts;

  public Ticket(String id, String question) {
    this.id = id;
    this.question = question;
  }

  public String getId() {
    return id;
  }

  public String getQuestion() {
    return question;
  }

  public TicketStatus getStatus() {
    return status;
  }

  public void setStatus(TicketStatus status) {
    this.status = status;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getResponse() {
    return response;
  }

  public void setResponse(String response) {
    this.response = response;
  }

  public int getAttempts() {
    return attempts;
  }

  public void setAttempts(int attempts) {
    this.attempts = attempts;
  }

}
