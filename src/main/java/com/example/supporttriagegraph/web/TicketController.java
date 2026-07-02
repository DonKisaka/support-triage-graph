package com.example.supporttriagegraph.web;

import com.example.supporttriagegraph.support.SupportService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

  private final SupportService supportService;

  public TicketController(SupportService supportService) {
    this.supportService = supportService;
  }

  @PostMapping
  public TicketResponse submit(@RequestBody SubmitRequest request) {
    return TicketResponse.from(supportService.submit(request.question()));
  }

  @PostMapping("/{ticketId}/classify")
  public TicketResponse classify(@PathVariable String ticketId,
                                 @RequestBody ClassifyRequest request) throws Exception {
    return TicketResponse.from(supportService.resume(ticketId, request.category()));
  }

  @GetMapping("/{ticketId}")
  public TicketResponse get(@PathVariable String ticketId) {
    return TicketResponse.from(supportService.get(ticketId));
  }

}
