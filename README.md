[![Runbook](https://img.shields.io/badge/runbook-view-172B4D.svg?logo=confluence)](https://dsdmoj.atlassian.net/wiki/spaces/NOM/pages/1739325587/DPS+Runbook)
[![repo standards badge](https://img.shields.io/badge/endpoint.svg?&style=flat&logo=github&url=https%3A%2F%2Foperations-engineering-reports.cloud-platform.service.justice.gov.uk%2Fapi%2Fv1%2Fcompliant_public_repositories%2Fhmpps-prisoner-from-nomis-migration)](https://operations-engineering-reports.cloud-platform.service.justice.gov.uk/public-report/hmpps-prisoner-from-nomis-migration "Link to report")
[![Docker Repository on ghcr](https://img.shields.io/badge/ghcr.io-repository-2496ED.svg?logo=docker)](https://ghcr.io/ministryofjustice/hmpps-prisoner-from-nomis-migration)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://prisoner-nomis-migration-dev.hmpps.service.justice.gov.uk/swagger-ui/index.html)

# hmpps-prisoner-from-nomis-migration

**Handles the migration of data related to prisoners in NOMIS to the new services**

**Currently one service is supported: Visit a Prisoner Service**

The purpose of this service is to bulk update a new service with data from NOMIS. This will handle the reading of data, possibly transformation of that data and pushing data 
to the new service.
Since migration is typically a process that takes many hours, this service relies heavily on SQS messaging to guarantee that the migration is completed, where any transient errors are
automatically retried. Any persistent errors can be inspected and are retained in a SQS dead letter queue.

# Running the service
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
- Set all environment variables to match those in [values-dev.yaml](/helm_deploy/values-dev.yaml) e.g. `API_BASE_URL_HMPPS_AUTH=https://sign-in-dev.hmpps.service.justice.gov.uk/auth`
- Set additional environment variables for personal **client credentials** you have that have the correct roles required to access the remotes services, the env names can be found in [values.yaml](helm_deploy/hmpps-prisoner-from-nomis-migration/values.yaml)
- Set additional environment variables for the SQS Queue secrets that can be found in the `hmpps-prisoner-from-nomis-migration-dev` namespace, again the env names can be found in [values.yaml](helm_deploy/hmpps-prisoner-from-nomis-migration/values.yaml)

A better hybrid solution which gives better control messaging would be similar to above but using the `dev` profile and therefore localstack.

The first 2 of the 3 steps is required but instead of step 3

- `docker-compose up localstack migration-db` or `docker compose up localstack migration-db` (there is also docker-compose-local.yaml with just localstack and postgres defined )

Then run any of the `bash` scripts at the root of this project to send events to the local topic

# Generating API client models

For some of our external API calls we use `openapi-generator` to generate the models used in the API clients. The Open API specifications used can be found in directory `openapi-specs`.

In the build.gradle.kts there is a `ModelConfiguration` for each of the models.  This contains the URL to be used to
obtain the JSON Open API specification and also the package name to be used for the generated models.  For each model
configuration two tasks are created:
1. `write<ModelName>Json` - this task will download the Open API specification and save it to `openapi-specs`.
2. `build<ModelName>ApiModel` - this task will generate the models from the specification

So, for example, running
```shell
./gradlew writeNonAssociationsJson compileKotlin compileTestKotlin
```

Will download the non associations Open API specification from dev, generate the model and then compile the code.
The json specification can then be committed to the repository.

### Generating the infrastructure client

The generated APIs rely on a generated org.openapitools.client.infrastructure.ApiClient. Since we have lots of generated
APIs we only want one version of the client, so we have copied the client to `src/main/kotlin`. Also, by default, the
client is generated with `protected` functions, but we need these to be public in order to call the methods.  Running:
```shell
generate-infrastructure-client.bash
```
will generate the client and copy it into `src/main/kotlin`, removing `protected` from the functions.

This will be necessary if the openapi generator is updated and the API clients that are generated no longer compile.

# Mock services

There are circumstances where you want to run this service end to end but without the consuming service being available, for example the consuming service
has not been written yet. To emulate the publishing service we may provide a mock, for instance MockVisitsResource which consumes migrated data.
Details of the configuration follows:

`API_BASE_URL_VISITS=https://prisoner-to-nomis-update-dev.hmpps.service.justice.gov.uk`


# Runbook

## Queue Dead letter queue maintenance

Since this services uses the HMPPS SQS library with defaults this has all the default endpoints for queue maintenance as documented in the [SQS library](https://github.com/ministryofjustice/hmpps-spring-boot-sqs/blob/main/README.md).

For purging queues the queue name can be found in the [health check](https://prisoner-nomis-migration.hmpps.service.justice.gov.uk/health) and the required role is the default `ROLE_QUEUE_ADMIN`.

## Visit Someone in Prison (VSIP)

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

### Application Insights Events

```azure
AppEvents 
| where AppRoleName == 'hmpps-prisoner-from-nomis-migration' 
| where Name contains "visit" 
| summarize count() by Name
```

will show all significant visit migration events

- `visits-migration-started` - the single event for a migration which will contain the estimate count and the migration id
- `visits-migration-completed` - the status of the migration id event. It will contain the migration id, estimate count and how long it took to complete the migration
- `visits-migration-entity-migrated` - the status of a single migrated visit. It will contain information on the prison, prisoner, visit, refernce and room.
- `nomis-migration-visit-mapping-failed`- indicates the visit was migrated but the mapping record could not be created. These events require manual intervention; see below.
- `nomis-migration-visit-no-room-mapping` - indicates a visit was not migrated because the room it was in could not be mapped. These events require room mapping change and a rerun of the migration.

`nomis-migration-visit-mapping-failed` requires the 2 IDs are extracted from App Insights and manually added to the mapping table via the API

```azure
AppEvents 
| where AppRoleName == 'hmpps-prisoner-from-nomis-migration' 
| where Name == 'nomis-migration-visit-mapping-failed'
| extend migrationId_ = tostring(Properties.migrationId)
| extend nomisVisitId_ = tostring(Properties.nomisVisitId)
| extend vsipVisitId_ = tostring(Properties.vsipVisitId)

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
## Sentencing Adjustments

### Synchronisation Duplicate handling

When a `From NOMIS synchronisation duplicate adjustment detected` alert is fired in the Alerts Slack channel manual steps are required to correct the data.

The alert happens when the check to see if an Adjustment already exists in DPS happens in two threads before either one has created an Adjustment in DPS yet. This can happen either multiple NOMIS events arrive at the same time or the creation of the DPS adjustment or mapping record hangs and succeeds at the same time.

Find the duplicate DPS adjustment ID from the AppInights query related to the Slack alert. This will be the `duplicateAdjustmentId` Property from the  `from-nomis-synch-adjustment-duplicate` AppEvent.

With this ID (which is a UUID string) call the DPS DELETE endpoint, e.g.

```bash
curl --location --request DELETE https://adjustments-api.hmpps.service.justice.gov.uk/legacy/adjustments/{dpsDuplicateAdjustmentId} \
--header 'Content-Type: application/vnd.nomis-offence+json' \
--header 'Authorization: Bearer <token with role SENTENCE_ADJUSTMENTS_SYNCHRONISATION >' 
```
## Contacts a.k.a Personal Relationships

DPS has different terminology for the 2 key entities to NOMIS:
---
* NOMIS: Person
* DPS: Contact

---
* NOMIS: Contact
* DPS: Prisoner Contact
---
#### Duplicate handling

Both DPS and NOMIS share teh same primary key for NOMIS person/DPS - so duplicates for this entity is impossible.  

Since a prisoner contact can not exist twice in either NOMIS or DPS; i.e. a prisoner and a civilian can not be related twice with the same type a network failure can result in an attempted duplicate.

These would result in a `from-nomis-sync-contactperson-duplicate` custom event
```ksql
AppEvents
| where Name == "from-nomis-sync-contactperson-duplicate"
| where AppRoleName == "hmpps-prisoner-from-nomis-migration"
```

The `type` indicates which of person contact entities has created the duplicate. 
 
For the scenario above the value would be `DPS_CONTACT`. Typically, we will still raise the alert in `#syscon-alerts` but the alert can be ignored if it only happens one since these duplicates are retried and eventually pushed to the DLQ.

To be confident, the single alert can be ignored run 
```ksql
let ops = AppEvents
| where AppRoleName == 'hmpps-prisoner-from-nomis-migration'
| where Name == "from-nomis-sync-contactperson-duplicate"
| project OperationId;
AppEvents
| where OperationId in (ops)
| where AppRoleName == 'hmpps-prisoner-from-nomis-migration'
```

You would then expect the following events:
 * `contactperson-contact-synchronisation-created-error`
 * `from-nomis-sync-contactperson-duplicate`
 * `contactperson-contact-synchronisation-created-success`

With the last event confirming that the duplicate was transient and has been resolved.

The other values are for mapping failures due to duplicate with values of:
* PERSON
* CONTACT
* ADDRESS
* PHONE
* EMAIL
* IDENTIFIER
* EMPLOYMENT
* CONTACT_RESTRICTION
* PERSON_RESTRICTION

In theory these should never happen anymore since they can only happen if a DPS POST succeeds and the mapping succeeds but the process takes longer then the SQS visibility timeout. Configuration should prevent this.
If it does happen the duplicate record in DPS would need to be deleted and the DLQ cleared. `#move_and_improve-syscon-collaboration` channel should be used to discuss this.

#### Repair

If for any reason the DPS data is out of sync with NOMIS but the NOMIS data is in the correct state you can manually repair the DPS data by essentially re-migrating the NOMIS data back over to DPS.

The scenario this might be necessary is of the mappings between DPS and NOMIS are themseleves incorrect due to a bug from when the mappings where first created.

To run the repair for just contacts related to a prisoner the run  
```
POST https://prisoner-nomis-migration.hmpps.service.justice.gov.uk/prisoners/{{repairOffenderNo}}/contacts/resynchronise
Authorization: Bearer {{$auth.token("hmpps-auth")}}
```

To run the repair for just teh entire person and all child entities the run 
```
POST https://prisoner-nomis-migration.hmpps.service.justice.gov.uk/persons/{{personId}}/resynchronise
Authorization: Bearer {{$auth.token("hmpps-auth")}}
```

The is currently not a repair that pushes data from DPS to NOMIS. There maybe a requirement for this in the future.

# Transactions Synchronisation
## Finance - Prison (GL_TRANSACTIONS) and Prisoner (OFFENDER_TRANSACTIONS)
The transactions sync has a feature that differs from other syncs: a grace period. This is because the DPS api requires that as much
data for a transaction as possible is sent in one go. Some transactions contain 1000s of DB rows.
```
When event arrives, check DB table of txn_ids:
if not there, add and requeue message for 5s time
if it is there then ignore event,
when re-queued message arrives: remove from cache if necessary, get data and call api
```

# Architecture

Architecture decision records start [here](doc/architecture/decisions/0001-use-adr.md)
