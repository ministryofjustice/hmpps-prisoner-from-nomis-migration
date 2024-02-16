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
- hmpps-nomis-mapping-service
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

- `docker-compose up localstack migration-db` or `docker compose up localstack migration-db` (there is also docker-compose-local.yaml with just localstack and postgres defined )

Then run any of the `bash` scripts at the root of this project to send events to the local topic

## Generating APi client models

For some of our external API calls we use `openapi-generator` to generate the models used in the API clients. The Open API specifications used can be found in directory `openapi-specs`.

### Updating the Open API specs

Run the following commands to take a copy of the latest specs:

```
curl https://nomis-prisoner-api-dev.prison.service.justice.gov.uk/v3/api-docs | jq . > openapi-specs/nomis-sync-api-docs.json
curl https://nomis-sync-prisoner-mapping-dev.hmpps.service.justice.gov.uk/v3/api-docs | jq . > openapi-specs/nomis-mapping-service-api-docs.json
curl https://activities-api-dev.prison.service.justice.gov.uk/v3/api-docs | jq . > openapi-specs/activities-api-docs.json
curl https://manage-adjudications-api-dev.hmpps.service.justice.gov.uk/v3/api-docs | jq . > openapi-specs/adjudications-api-docs.json
curl https://adjustments-api-dev.hmpps.service.justice.gov.uk/v3/api-docs | jq . > openapi-specs/sentencing-adjustments-api-docs.json
curl https://incident-reporting-api-dev.hmpps.service.justice.gov.uk/v3/api-docs | jq . > openapi-specs/incidents-api-docs.json
curl https://alerts-api-dev.hmpps.service.justice.gov.uk/v3/api-docs | jq . > openapi-specs/alerts-api-docs.json
```

Go into the specs and reformat so they and the diffs are easier for humans to read.

Then run another command to regenerate the models in the `build/generated/src` directory:

`./gradlew clean compileKotlin`

Now build the project and deal with any compile errors caused by changes to the generated models.

Finally run the tests and fix any issues caused by changes to the models.

### Adding new Open API specs

Add the instructions for the curl command above but obviously with a different file name

In the build.gradle add a new task similar to the `buildActivityApiModel` task
In the build.gradle add dependencies in the appropriate tasks e.g. in `withType<KotlinCompile>` for the new task

## Mock services

There a circumstances where you want to run this service end to end but without the consuming service being available, for example the consuming service
has not be written yet. To emulate the publishing service we may provide a mock, for instance MockVisitsResource which consumes migrated data.
Details of the configuration follows:

`API_BASE_URL_VISITS=https://prisoner-to-nomis-update-dev.hmpps.service.justice.gov.uk`


### Runbook

#### Queue Dead letter queue maintenance

Since this services uses the HMPPS SQS library with defaults this has all the default endpoints for queue maintenance as documented in the [SQS library](https://github.com/ministryofjustice/hmpps-spring-boot-sqs/blob/main/README.md).

For purging queues the queue name can be found in the [health check](https://prisoner-nomis-migration.hmpps.service.justice.gov.uk/health) and the required role is the default `ROLE_QUEUE_ADMIN`.

#### Visit a Person in Prison (VSIP)

With the kubernetes pods scaled to around 12, around `200,000` visits can be migrated per hour.

A migration can be started by calling the migration end point using a HMPPS Auth token with the role `MIGRATE_VISITS`

`POST /migrate/visits`

```bash

curl --location --request POST 'https://prisoner-nomis-migration.hmpps.service.justice.gov.uk/migrate/visits' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer <token with MIGRATE_VISITS>' \
--data-raw '{
"prisonIds": ["HEI"],
"visitTypes": ["SCON"]
}'
```

POST body to optionally contain:

- `prisonIds` - a list of prison ids to migrate
- `visitTypes` - a list of visit types to migrate. It will default to `SCON` if not provided.
- `fromDateTime` - only include visits created after this date. NB this is creation date not the actual visit date.
- `endDateTime` - only include visits created before this date. NB this is creation date not the actual visit date.
- `ignoreMissingRoom` - When true exclude visits without an associated room (visits created during the VSIP synchronisation process), defaults to false. Only required during testing when mapping records are manually deleted.


The `from` and `end` times are primarily used to ensure that only visits created after the last migration are migrated. This is a performance optimisation 
since visits will never be migrated twice. Once it is marked as migrated it can only be migrated again by resetting the visit mapping table.

The response from this call will be an information JSON structure which echos the filter used but also includes:
- `migrationId` - the id of the migration that groups Application Insights events and is also written to the visit mapping table
- `estimatedCount` - the estimated number of visits that will be migrated. Since the last page of visits may include new visits created in NOMIS while the migration is running this number might change.

Given the health check page reports the number of messages in the main queue and DLQ, it is possible to see at a glance how far the migration has progressed from the `/health` endpoint.

##### Application Insights Events

```azure
customEvents 
| where cloud_RoleName == 'hmpps-prisoner-from-nomis-migration' 
| where name startswith "nomis-migration" 
| summarize count() by name
```

will show all significant visit migration events

- `nomis-migration-visits-started` - the single event for a migration which will contain the estimate count and the migration id
- `nomis-migration-visit-migrated` - the event for each visit migrated. It will contain the visit ids and basic information about the visit
- `nomis-migration-visit-mapping-failed`- indicates the visit was migrated but the mapping record could not be created. These events require manual intervention; see below.
- `nomis-migration-visit-no-room-mapping` - indicates a visit was not migrated because the room it was in could not be mapped. These events require room mapping change and a rerun of the migration.

`nomis-migration-visit-mapping-failed` requires the 2 IDs are extracted from application insights and manually added to the mapping table via the API

```azure
customEvents 
| where cloud_RoleName == 'hmpps-prisoner-from-nomis-migration' 
| where name == 'nomis-migration-visit-mapping-failed'
| extend migrationId_ = tostring(customDimensions.migrationId)
| extend nomisVisitId_ = tostring(customDimensions.nomisVisitId)
| extend vsipVisitId_ = tostring(customDimensions.vsipVisitId)

```
For each failure the mapping endpoint should be called to create the mapping record.
e.g.

```bash
curl --location --request POST 'https://nomis-sync-prisoner-mapping.hmpps.service.justice.gov.uk/mapping' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer <token with ADMIN_NOMIS >' \
--data-raw '{
    "mappingType": "MIGRATED",
    "nomisId": 624145,
    "vsipId": "6b111f4c-6593-412e-8ec1-685486962e09",
    "label": "2022-02-14T09:58:45"
}'
```
#### Adjudications
 
##### DSP Migration 500 error resolution

A 500 error returned from DPS service `POST /reported-adjudications/migrate` typically means we have previously migrated an adjudication and the migration service is trying to migrate the same record again.
This can happen when the `POST /reported-adjudications/migrate` was successful but a network issue meant the migration service never received the response. The migration service will retry the migration and the DPS service will return a 500 error since it detects a duplicate record.

Records in this state will end up on the DLQ and this query will find the unique adjudications:

```azure
traces
| where timestamp > todatetime('2023-09-26T11:00:00')
| where timestamp < todatetime('2023-09-26T11:00:20')
| where operation_Name == "POST /reported-adjudications/migrate"
| summarize by  message
```

Where the correct time range for a retry attempt is supplied. This should return results something like:

```ERROR: duplicate key value violates unique constraint "unique_report_number" Detail: Key (charge_number)=(3057925-1) already exists.```

Given this we have enough information to call the NOMIS API and the DPS Adjudication API to manually create a mapping record.

Call the NOMIS API as follows:

Requires a token with `NOMIS_ADJUDICATIONS` 

`https://nomis-prisoner.aks-live-1.studio-hosting.service.justice.gov.uk/adjudications/adjudication-number/3057925/charge-sequence/1`

From this extract the prison id (agency Id) for the incident since this is needed for the DPS call.

Call the DPS API as follows:

Requires a token with `VIEW_ADJUDICATIONS`

Set Header `Active-Caseload` to the prison id from the NOMIS call
`https://manage-adjudications-api.hmpps.service.justice.gov.uk/reported-adjudications/3057925-1/v2`


Lastly we need to create manually a mapping record by POSTing an adjudication mapping record to the mapping service.

POST url is `https://nomis-sync-prisoner-mapping.hmpps.service.justice.gov.uk/mapping/adjudications/all`

Requires a token with `NOMIS_ADJUDICATIONS`

Body would be similar to this 
```json
{
  "label": "2023-09-26T07:59:02",
  "mappingType": "MIGRATED",
  "adjudicationId": {
        "adjudicationNumber": "3057925",
        "chargeNumber": "3057925-1",
        "chargeSequence": "1"
    },
    "hearings": [
        {
            "dpsHearingId": "3755641",
            "nomisHearingId": "3434190"
        }
    ],
    "punishments": [
        {
            "dpsPunishmentId": "2980481",
            "nomisBookingId": "2375870",
            "nomisSanctionSequence": "13"
        },
        {
            "dpsPunishmentId": "2980479",
            "nomisBookingId": "2375870",
            "nomisSanctionSequence": "14"
        }
    ]
}
```
where the IDs are extracted from the DPS and NOMIS calls and where label is this migration id.
### Architecture

Architecture decision records start [here](doc/architecture/decisions/0001-use-adr.md)
