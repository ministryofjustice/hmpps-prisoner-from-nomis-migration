# 2. Single migration service used for all prisoner domain events

[Next >>](9999-end.md)


Date: 2022-01-31

## Status

Accepted

## Context


New services that require a bulk migration from NOMIS require a stable, re-runnable process. The basic infrastructure for that migration is likely 
to be similar for all migrations using SQS and dead letter queues.

For that service there are several options:
- One service for all prison related migrations
- One service for each consuming service

Furthermore, these services will be owned and maintained by the Syscon team since they are focused on keeping NOMIS is sync with external systems. 

## Decision

We will have one service for all prisoner related services this will eventually integrate with many new services, the first of which will be visits (VSIP).

## Consequences

- This reduces the number of services required to be maintained, and given these services will be retired when NOMIS is retired this should reduce the maintenance overhead on a single team (Syscon) maintaining dozens of services.
- This will increase the complexity of this service since it will need to interact with multiple external services and the `prisoner` domain is obviously very large in NOMIS, so we expect many new services creating data.
- Mitigation of the above needs careful structuring of the service (e.g. using packages/namespaces)
- It might be appropriate to remove code that is no longer needed for a service that has full migrated


[Next >>](9999-end.md)
