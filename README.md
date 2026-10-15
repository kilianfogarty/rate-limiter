# rate-limiter
Became interested in implementing a distributed rate-limiter like Stripe described in this engineering blog: https://stripe.com/blog/rate-limiters

It discusses four different types of limiters:

1. Request rate limiter - restricts a user to N number of requests per second. I have implemented this with a set bucket capacity per second and a set refill rate
2. Concurrent requests limiter - the other method of rate limiting: the user can only have M API requests in progress at the same time
3. Fleet usage load shedder - ensures that a certain percentage of your fleet will always be available for your most important API requests
4. Work utilization load shedder -  If your workers or job queue start getting backed up with requests, then this will shed lower-priority traffic.

Obviously this implementation is going to differe in some ways. As far as I am aware, Stripe still maintains a Ruby monolith, and their limiter logic resides within that rather than within its own microservice.

