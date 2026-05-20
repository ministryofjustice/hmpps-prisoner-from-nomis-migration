# From and To NOMIS Synchronisation and Migration

This repository consists of three services:
- [hmpps-prisoner-from-nomis-migration](from-nomis) - the service that reads NOMIS data and pushes it to DPS
- [hmpps-prisoner-to-nomis-update](to-nomis) - the service that reads DPS data and pushes it to NOMIS
- [hmpps-nomis-mapping-service](nomis-mapping) - the service that holds the mappings between NOMIS and DPS
