package com.kilianfogarty.ratelimiter.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RateLimiterService {

  private final StringRedisTemplate redis;
  private final DefaultRedisScript<Long> tokenBucketScript;

  private final double REPLENISH_RATE = 100;
  private final double CAPACITY = 5 * REPLENISH_RATE;

  // Measured in tokens
  @Value("${rate-limit.capacity:10}")
  private int capacity;

  // Measured in tokens per second
  @Value("${rate-limit.refill-rate:5}")
  private double refillRate;

  public RateLimiterService(StringRedisTemplate redis) {
    this.redis = redis;

    this.tokenBucketScript = new DefaultRedisScript<>();
    this.tokenBucketScript.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
    this.tokenBucketScript.setResultType(Long.class);
  }

  /**
   * Decides whether the client's request is allowed based on token usage. Uses clientId for a
   * per-customer basis instead of API key for security, to allow multiple keys per-customer, and to
   * prevent key rotation.
   *
   * @param clientId
   * @return true if request allowed, false if not (boolean)
   * @throws IllegalArgumentException if clientId is empty
   */
  public boolean allowRequest(String clientId) {
    if (clientId == null || clientId.isBlank()) {
      throw new IllegalArgumentException("Client API Key ");
    }

    if (capacity <= 0 || !Double.isFinite(refillRate) || refillRate <= 0) {
      throw new IllegalStateException("Rate-limit capacity and refill rate must be positive");
    }

    // set 1000 milliseconds as the minimum bucket expiry time to ensure that on systems with small
    // capacities or
    // refill rates the TTL does not go below one second. Keeps bucket around for however many
    // milliseconds to refill, then doubles it for an arbitrary margin.

    long ttlMillis = Math.max(1000L, (long) Math.ceil(capacity / refillRate * 2000));

    String bucketKey = "rate-limit:" + clientId;

    try {
      Long result =
          redis.execute(
              tokenBucketScript,
              List.of(bucketKey),
              String.valueOf(capacity),
              String.valueOf(refillRate),
              String.valueOf(ttlMillis));

      if (result == null) {
        throw new RateLimiterUnavailableException("Redis returned no rate-limit decision");
      }

      return result == 1L;

    } catch (DataAccessException e) {
      throw new RateLimiterUnavailableException("Could not execute rate-limit check.", e);
    }
  }

  public static class RateLimiterUnavailableException extends RuntimeException {
    public RateLimiterUnavailableException(String message) {
      super(message);
    }

    public RateLimiterUnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
