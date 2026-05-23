package com.eventledger.api.controller;

import com.eventledger.api.dto.EventRequest;
import com.eventledger.api.dto.EventResponse;
import com.eventledger.api.repository.EventRepository;
import com.eventledger.api.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final EventRepository eventRepository;

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody EventRequest request) {
        boolean alreadyExists = eventRepository.existsById(request.getEventId());
        EventResponse response = eventService.createEvent(request);
        HttpStatus status = alreadyExists ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable String id) {
        return ResponseEntity.ok(eventService.getEvent(id));
    }

    @GetMapping
    public ResponseEntity<List<EventResponse>> getEventsByAccount(@RequestParam("account") String accountId) {
        return ResponseEntity.ok(eventService.getEventsByAccount(accountId));
    }
}
