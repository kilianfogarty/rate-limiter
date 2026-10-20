# rate-limiter
Became interested in implementing a distributed rate-limiter like Stripe described in this engineering blog: 

https://stripe.com/blog/rate-limiters

and their subsequent gist, written in Ruby:

https://gist.github.com/ptarjan/e38f45f2dfe601419ca3af937fff574d

Also, check out this implementation from Lyft in Go:

https://github.com/envoyproxy/ratelimit

Hashicorp's LRU implementation:

https://github.com/hashicorp/golang-lru/tree/main

Useful when I introduce a cache to store a fixed size number of keys that are currently maxed out?


Algorithms:
https://www.geeksforgeeks.org/system-design/rate-limiting-algorithms-system-design/
https://arcjet.com/learn/rate-limiting-guide


It discusses four different types of limiters:

1. Request rate limiter - restricts a user to N number of requests per second. I have implemented this with a set bucket capacity per second and a set refill rate
2. Concurrent requests limiter - the other method of rate limiting: the user can only have M API requests in progress at the same time
3. Fleet usage load shedder - ensures that a certain percentage of your fleet will always be available for your most important API requests
4. Work utilization load shedder -  If your workers or job queue start getting backed up with requests, then this will shed lower-priority traffic.

Obviously this implementation is going to differe in some ways. As far as I am aware, Stripe still maintains a Ruby monolith, and their limiter logic resides within that rather than within its own microservice.

Use YAML files for config. When doing in Go later use makefile

Talk about fixed window like Lyft vs token bucket like Stripe

# Rate Limiter Roadmap

A Redis-backed distributed rate limiter built on ideas from
[Stripe's rate limiter post](https://stripe.com/blog/rate-limiters),
[Stripe's reference gist](https://gist.github.com/ptarjan/e38f45f2dfe601419ca3af937fff574d),
and [Lyft/Envoy ratelimit](https://github.com/envoyproxy/ratelimit).

**Stack:** Java 21, Spring Boot, Redis (Lua scripts), Docker Compose

## Progress

| Step | Topic | Status |
|---|---|---|
| 1 | Token bucket (Lua) | Done |
| 2 | Failure behavior (fail open) | Code written, needs Redis-down test |
| 3 | Time and distribution | Next |
| 4 | Tests | Todo |
| 5 | Rules from YAML | Todo |
| 6 | Shadow mode and kill switch | Todo |
| 7 | Concurrent requests limiter | Todo |
| 8 | Metrics and response headers | Todo |
| 9 | Load shedders | Todo |

## Steps

### 1. Token bucket (done)
- Lazy, continuous refill: no timers, tokens credited on each request from elapsed time.
- `tokens` and `last_refill_ms` are one unit and must be written together.
- Fractional tokens are kept so no refill time is lost (flooring starves clients).
- Lua makes read-compute-write atomic in Redis.

### 2. Failure behavior
- Redis down means the limiter steps aside (fail open): log, count, continue to the real handler.
- Only `RateLimiterUnavailableException` fails open. A blank client ID is rejected,
  and bad config should fail at startup.
- Add: `ratelimiter.fail_open` metric so silent failures are visible.

### 3. Time and distribution
- The script currently receives `System.currentTimeMillis()` from each app instance.
  With multiple instances, clock skew corrupts the bucket.
- Use Redis `TIME` inside the script as the single clock.
- Revisit the TTL formula (2x fill time) and document why.
- Note single-key hot spots and Redis Cluster hash-tag implications.

### 4. Tests
- Unit tests for the algorithm with a controllable clock.
- Integration tests against real Redis (Testcontainers or Compose).
- Concurrency test: many threads, one key, allowed count never exceeds capacity + refill.
- Failure test: stop Redis mid-run and assert requests still pass.

### 5. Rules from YAML (Lyft's model)
- Domains and descriptors (key/value pairs), nested descriptors, most-specific match.
- Per-rule algorithm, rate, burst; unlimited descriptors skip Redis.
- Hot reload of config.
- Move the check out of the controller into a filter/interceptor so it applies to any endpoint.
- Validate config at startup and fail fast.

### 6. Shadow mode and kill switch (Stripe's dark launch)
- Per-rule and global `shadow_mode`: evaluate and record, but never block.
- Kill switch to disable the limiter without a deploy.
- Use 429 for client over limit, 503 for load shedding.

### 7. Concurrent requests limiter
- Redis ZSET of in-flight request IDs with timestamps; reject at `max_in_flight`.
- Acquire before work, release in `finally`, so failures don't leak slots.
- TTL cleanup for lost requests, done atomically inside Lua.

### 8. Metrics and response headers
- Per-rule counters: total_hits, near_limit, over_limit, shadow_mode.
- Prometheus endpoint, health check, structured logs.
- `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset` and `Retry-After` headers.

### 9. Load shedders
- Fleet usage shedder: concurrent limiter on a global key, split critical vs non-critical traffic.
- Worker utilization shedder: shed gradually by priority tier, recover slowly to avoid flapping.
- Local cache of over-limit keys to cut Redis round trips (Lyft).

## Finishing touches
- Dockerfile and Compose demo (app, Redis, Prometheus, Grafana).
- Load test with k6 or wrk; report only measured throughput and latency.
- `DESIGN.md`: algorithm tradeoffs (token bucket vs fixed vs sliding window), failure modes, what changes at scale.
- Short runbook: disable the limiter, roll out a rule in shadow mode, alerts to set.

## Concepts learned so far
- The limiter is the decision (allow/deny), not the key-value store.
- Lazy evaluation (when the math runs) vs continuous refill (what the math does).
- Fixed-window counters (Lyft) need no script; token buckets (Stripe) do, because
  they read two fields and write two fields.
- `finally` runs even after a `return`, so it is for cleanup, not for the real work.
