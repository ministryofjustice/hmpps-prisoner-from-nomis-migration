[![CircleCI](https://circleci.com/gh/ministryofjustice/hmpps-prisoner-from-nomis-migration/tree/main.svg?style=svg)](https://circleci.com/gh/ministryofjustice/hmpps-prisoner-from-nomis-migration)
[![Docker Repository on Quay](https://quay.io/repository/hmpps/hmpps-prisoner-from-nomis-migration/status "Docker Repository on Quay")](https://quay.io/repository/hmpps/hmpps-prisoner-from-nomis-migration)
[![Runbook](https://img.shields.io/badge/runbook-view-172B4D.svg?logo=confluence)](https://dsdmoj.atlassian.net/wiki/spaces/NOM/pages/1739325587/DPS+Runbook)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://prisoner-nomis-migration-dev.hmpps.service.justice.gov.uk/swagger-ui.html)

# hmpps-prisoner-from-nomis-migration

**Handles the migration of data related to prisoners in NOMIS to the new services**

**Currently one service is supported: Visit a Prisoner Service**

The purpose of this service to bulk update a new service with data from NOMIS. This will handle the reading of data, possibly transformation of that data and pushing data 
to the new service.
Since migration is a typically a process that takes many hours, this service relies heavily on SQS messaging to guarantee that the migration is completed, where any transient errors are
automatically retried. Any persistent errors can be inspected and are retained in a SQS dead letter queue.

## Running locally

For running locally against docker instances of the following services:
- hmpps-auth
- hmpps-nomis-prisoner-api
- visit-scheduler
- hmpps-nomis-visits-mapping-service
- localstack
- run this application independently e.g. in IntelliJ

`docker compose up --scale hmpps-prisoner-from-nomis-migration=0`

or

`docker-compose up  --scale hmpps-prisoner-from-nomis-migration=0`

Running all services including this service

`docker compose up`

or

`docker-compose up`

## Running locally against T3 test services

Though it is possible to run this service against both the test services deployed in the cloud **and** the SQS queue in AWS this is not recommended while the deployed version of this service is running in the Cloud Platform since there is no guarantee this local instance will read an incoming HMPPS domain event since other pods are also reading from the same queue.

However, if you do wish to do that the steps would be:
- Set all environment variables to match those in [values-dev.yaml](/helm_deploy/values-dev.yaml) e.g. `API_BASE_URL_OAUTH=https://sign-in-dev.hmpps.service.justice.gov.uk/auth`
- Set additional environment variables for personal **client credentials** you have that have the correct roles required to access the remotes services, the env names can be found in [values.yaml](helm_deploy/hmpps-prisoner-from-nomis-migration/values.yaml)
- Set additional environment variables for the SQS Queue secrets that can be found in the `hmpps-prisoner-from-nomis-migration-dev` namespace, again the env names can be found in [values.yaml](helm_deploy/hmpps-prisoner-from-nomis-migration/values.yaml)

A better hybrid solution which gives better control messaging would be similar to above but using the `dev` profile and therefore localstack.

The first 2 of the 3 steps is required but instead of step 3

- `docker-compose up localstack` or `docker compose up localstack` (there is also docker-compose-localstack.yaml with just localstack defined )

Then run any of the `bash` scripts at the root of this project to send events to the local topic

## Mock services

There a circumstances where you want to run this service end to end but without the consuming service being available, for example the consuming service
has not be written yet. To emulate the publishing service we may provide a mock, for instance MockVisitsResource which consumes migrated data.
Details of the configuration follows:


### Runbook

#### Queue Dead letter queue maintenance

Since this services uses the HMPPS SQS library with defaults this has all the default endpoints for queue maintenance as documented in the [SQS library](https://github.com/ministryofjustice/hmpps-spring-boot-sqs/blob/main/README.md).

For purging queues the queue name can be found in the [health check](https://prisoner-nomis-migration.hmpps.service.justice.gov.uk/health) and the required role is the default `ROLE_QUEUE_ADMIN`.



### Architecture

Architecture decision records start [here](doc/architecture/decisions/0001-use-adr.md)
