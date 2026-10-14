package com.kilianfogarty.ratelimiter.controller;

import com.kilianfogarty.ratelimiter.service.RateLimiterService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Want to fail open, not closed if the rate limiter service goes down

@RestController
@RequestMapping("/v1")
public class RateLimiterController {

  private final RateLimiterService rateLimiterService;

  public RateLimiterController(RateLimiterService rateLimiterService) {
    this.rateLimiterService = rateLimiterService;
  }

  @GetMapping("/demo")
  public ResponseEntity<ApiResponse> demo(
      @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

    if (apiKey == null || apiKey.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(new ApiResponse("Missing X-API-Key header", false));
    }

    try {
      boolean allowed = rateLimiterService.allowRequest(apiKey);

      if (!allowed) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(new ApiResponse("Rate limit exceeded", false));
      }

      return ResponseEntity.ok(new ApiResponse("Request allowed", true));

    } catch (RateLimiterService.RateLimiterUnavailableException e) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(new ApiResponse("Rate limiter unavailable", false));
    }
  }

  public record ApiResponse(String message, boolean success) {}
}
