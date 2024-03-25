package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper

fun pageContent(
  content: String,
  pageSize: Long,
  pageNumber: Long,
  totalElements: Long,
  size: Int,
) =
  // language=json
  """
{
    "content": [
        $content
    ],
    "pageable": {
        "sort": {
            "empty": false,
            "sorted": true,
            "unsorted": false
        },
        "offset": 0,
        "pageSize": $pageSize,
        "pageNumber": $pageNumber,
        "paged": true,
        "unpaged": false
    },
    "last": false,
    "totalPages": ${totalElements / pageSize + 1},
    "totalElements": $totalElements,
    "size": $pageSize,
    "number": $pageNumber,
    "sort": {
        "empty": false,
        "sorted": true,
        "unsorted": false
    },
    "first": true,
    "numberOfElements": $size,
    "empty": false
}
  """.trimIndent()
fun pageContent(
  objectMapper: ObjectMapper,
  content: List<Any>,
  pageSize: Long,
  pageNumber: Long,
  totalElements: Long,
  size: Int,
) =
  // language=json
  """
{

    "content": ${objectMapper.writeValueAsString(content)},
    "pageable": {
        "sort": {
            "empty": false,
            "sorted": true,
            "unsorted": false
        },
        "offset": 0,
        "pageSize": $pageSize,
        "pageNumber": $pageNumber,
        "paged": true,
        "unpaged": false
    },
    "last": false,
    "totalPages": ${totalElements / pageSize + 1},
    "totalElements": $totalElements,
    "size": $pageSize,
    "number": $pageNumber,
    "sort": {
        "empty": false,
        "sorted": true,
        "unsorted": false
    },
    "first": true,
    "numberOfElements": $size,
    "empty": false
}
  """.trimIndent()
