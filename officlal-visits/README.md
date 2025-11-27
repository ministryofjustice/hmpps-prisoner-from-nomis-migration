# Official Visits data mapping

## NOMIS Screen Mapping

![ScheduledVisit.png](ScheduledVisit.png)

### Visits section

| Form label                   | NOMIS Table             | Column                                                         | API Attribute                                           | DPS Attribute                            | Notes                 |
|:-----------------------------|:------------------------|:---------------------------------------------------------------|:--------------------------------------------------------|:-----------------------------------------|:----------------------|
| Visit Order                  | OFFENDER_VISIT_ORDERS   | VISIT_ORDER_NUMBER                                             | n/a                                                     | n/a                                      |                       |
| Visit Type                   | OFFENDER_VISITS         | VISIT_TYPE (REF: VISIT_TYPE)                                   | n/a                                                     | visitTypeCode                            |                       |
| Date                         | OFFENDER_VISITS         | VISIT_DATE                                                     | startDateTime                                           | visitDate                                |                       |
| Timeslot                     | OFFENDER_VISITS         | AGENCY_VISIT_SLOT_ID                                           | visitSlotId                                             | prisonVisitSlotId via slot mapping       |                       |
| Location                     | OFFENDER_VISITS         | VISIT_INTERNAL_LOCATION_ID                                     | internalLocationId                                      | dpsLocationId via location mapping       |                       |
| Start                        | OFFENDER_VISITS         | START_TIME                                                     | startDateTime                                           | startTime                                |                       |
| End                          | OFFENDER_VISITS         | END_TIME                                                       | endDateTime                                             | endTime                                  |                       |
| Cancel Reason                | OFFENDER_VISIT_VISITORS | OUTCOME_REASON_CODE (REF: MOV_CANC_RS)                         | cancellationReason                                      | outcomeReasonCode                        |                       |
| Completion                   | OFFENDER_VISITS         | VISIT_STATUS (REF: VIS_COMPLETE)                               | visitStatus                                             | visitStatusCode                          | SCH before completion |
| Attended                     | OFFENDER_VISIT_VISITORS | EVENT_OUTCOME (REF: OUTCOMES)                                  | prisonerAttendanceOutcome                               | eventOutcomeCode                         |                       |
| n/a                          | OFFENDER_VISITS         | EVENT_OUTCOME                                                  | n/a                                                     | n/a                                      | Not used              |
| n/a                          | OFFENDER_VISITS         | OUTCOME_REASON_CODE                                            | n/a                                                     | n/a                                      | Not used              |
| Banned Visitor Authorisation | OFFENDER_VISITS         | OVERRIDE_BAN_STAFF_ID (via STAFF via STAFF_USER_ACCOUNTS)      | overrideBanStaffUsername                                | overrideBanStaffUsername                 |                       |
| Comments                     | OFFENDER_VISITS         | COMMENT_TEXT                                                   | commentText                                             | commentText                              |                       |
| Search Type                  | OFFENDER_VISITS         | SEARCH_TYPE (REF: SEARCH_LEVEL)                                | visitStatus                                             | visitStatusCode                          | searchTypeCode        |
| Visitor Concerns             | OFFENDER_VISITS         | VISITOR_CONCERN_TEXT                                           | visitorConcernText                                      | visitorConcernText                       |                       |

### Visitors section

| Form label                  | NOMIS Table                    | Column                        | API Attribute                           | DPS Attribute              | Notes                               |
|:----------------------------|:-------------------------------|:------------------------------|:----------------------------------------|:---------------------------|:------------------------------------|
| A P V U                     | OFFENDER_VISIT_VISITORS        | ASSISTED_VISIT_FLAG           | visitors.assistedVisit                  | assistedVisitFlag          |                                     |
| Visitor ID                  | OFFENDER_VISIT_VISITORS        | PERSON_ID                     | visitors.personId                       | personId                   |                                     |
| Last Name                   | PERSON                         | LAST_NAME                     | visitors.lastName                       | visitors.lastName          |                                     |
| First Name                  | PERSON                         | FIRST_NAME                    | visitors.firstName                      | visitors.firstName         |                                     |
| Contact Type                | OFFENDER_CONTACT_PERSONS       | CONTACT_TYPE                  | n/a                                     | n/a                        | Does DPS need this ?                |
| Relationship                | OFFENDER_CONTACT_PERSONS       | RELATIONSHIP_TYPE             | visitors.relationships.relationshipType | visitors.relationshipType  | This is a many to one               |
| Age                         | PERSON                         | BIRTHDATE                     | visitors.dateOfBirth                    | visitors.dateOfBirth       |                                     |
| Restriction                 | OFFENDER_PERSON_RESTRICTS      | n/a                           | n/a                                     | n/a                        |                                     |
| Estate Wide                 | VISITOR_RESTRICTIONS           | n/a                           | n/a                                     | n/a                        |                                     |
| Attended                    | OFFENDER_VISIT_VISITORS        | EVENT_OUTCOME (REF: OUTCOMES) | visitors.visitorAttendanceOutcome       | visitor.eventOutcomeCode   |                                     |
| Comments                    | OFFENDER_VISIT_VISITORS        | COMMENT_TEXT                  | visitors.commentText                    | visitors.commentText       |                                     |
| n/a                         | OFFENDER_VISIT_VISITORS        | OUTCOME_REASON_CODE           | visitors.cancellationReason             | visitors.outcomeReasonCode | Not displayed in NOMIS              |
| n/a                         | OFFENDER_VISIT_VISITORS        | EVENT_STATUS                  | visitors.eventStatus                    | n/a                        | Not displayed in NOMIS SCH,EXP,COMP |
| n/a                         | OFFENDER_VISIT_VISITORS        | EVENT_ID                      | n/a                                     | n/a                        | Not displayed in NOMIS              |


## Scheduled Visit

### NOMIS Screen

![ScheduledVisit.png](ScheduledVisit.png)


