package com.kilianfogarty.ratelimiter.controller;

import com.kilianfogarty.ratelimiter.service.RateLimiterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

// Want to fail open, not closed if the rate limiter service goes down

@RestController
@RequestMapping("/v1")
public class RateLimiterController {

  private final RateLimiterService rateLimiterService;

  // Logger and counter for documenting and logging failures
  private static final Logger log = LoggerFactory.getLogger(RateLimiterController.class);
  private final Counter failOpenCounter;

  public RateLimiterController(RateLimiterService rateLimiterService, MeterRegistry registry) {

    this.rateLimiterService = rateLimiterService;
    this.failOpenCounter =
        Counter.builder("ratelimiter.fail_open")
            .description("Requests let through because the limiter was unavailable")
            .register(registry);
  }

  @GetMapping("/demo")
  public ResponseEntity<ApiResponse> demo(
      @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

    if (apiKey == null || apiKey.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(new ApiResponse("Missing X-API-Key header", false));
    }

    // Since this should fail open, the only logic that resides in the try block and stops the user from progressing
    // is if they have used all of their tokens. Otherwise it passes through to the Request allowed return statement.
    // Same goes for the catch statement. It is just logged and falls through to return statement below to ensure
    // that services remain reachable even if the rate limiter fails.
    try {
      boolean allowed = rateLimiterService.allowRequest(apiKey);

      if (!allowed) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(new ApiResponse("Rate limit exceeded", false));
      }

    } catch (RateLimiterService.RateLimiterUnavailableException e) {
      log.error("Rate limiter unavailable, failing open", e);
      failOpenCounter.increment();
    }

    return ResponseEntity.ok(new ApiResponse("Request allowed", true));

  }

  public record ApiResponse(String message, boolean success) {}
}
