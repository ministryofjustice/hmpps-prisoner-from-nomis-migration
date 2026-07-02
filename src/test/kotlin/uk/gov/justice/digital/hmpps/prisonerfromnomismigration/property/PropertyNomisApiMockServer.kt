package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PropertyContainerCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PropertyContainerGetResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.lang.Long.min
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class PropertyNomisApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubMultipleGetPropertyIdCounts(totalElements: Long, pageSize: Long) {
    // for each page create a response for each id starting from 1 up to `totalElements`

    val pages = (totalElements / pageSize) + 1
    (0..pages).forEach { page ->
      val startId = (page * pageSize) + 1
      val endId = min((page * pageSize) + pageSize, totalElements)
      nomisApi.stubFor(
        get(urlPathEqualTo("/property-containers/ids"))
          .withQueryParam("page", equalTo(page.toString()))
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(
                propertyIdsPagedResponse(
                  totalElements = totalElements,
                  ids = (startId..endId).toList(),
                  pageNumber = page,
                  pageSize = pageSize,
                ),
              ),
          ),
      )
    }
  }

  fun stubMultipleGetProperty(intProgression: IntProgression) {
    (intProgression).forEach {
      nomisApi.stubFor(
        get(urlPathEqualTo("/property-containers/$it"))
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(propertyResponse(it.toLong())),
          ),
      )
    }
  }

  fun stubGetProperty(
    dpsId: Long = 1001,
    bookingId: Long = 123456,
    propertyResponse: PropertyContainerGetResponse = propertyContainerGetResponse(bookingId),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/property-containers/$dpsId")).willReturn(
        okJson(jsonMapper.writeValueAsString(propertyResponse)),
      ),
    )
  }

  fun stubPutProperty(
    dpsId: Long,
    status: HttpStatus = HttpStatus.OK,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/property-containers/$dpsId")).willReturn(status(status.value())),
    )
  }

  fun stubDeleteProperty(
    dpsId: Long,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/property-containers/$dpsId")).willReturn(status(HttpStatus.NO_CONTENT.value())),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun propertyContainerGetResponse(bookingId: Long) = PropertyContainerGetResponse(
  containerId = 1234567,
  bookingId = bookingId,
  offenderNo = "A1234AA",
  prisonId = "SYI",
  active = true,
  containerCode = PropertyContainerCode.BULK,
  createdDateTime = LocalDateTime.now(),
  createdBy = "ME",
  internalLocationId = 123456,
  sealMark = "SEAL1234",
  expiryDate = LocalDate.parse("2035-05-13"),
  proposedDisposalDate = LocalDate.parse("2035-05-14"),
)

private fun propertyResponse(
  containerId: Long,
  bookingId: Long = 2,
  offenderNo: String = "G4803UT",
): String =
  """
{
  "containerId": $containerId,
  "bookingId":$bookingId,
  "offenderNo": "$offenderNo",
  "prisonId": "LEI",
  "internalLocationId": 123456,
  "createdBy": "ME",
  "createdDateTime": "2023-01-01T11:00:01.234567",
  "active": true,
  "containerCode": "BULK",
  "sealMark": "SEAL1234",
  "expiryDate": "2035-05-13",
  "proposedDisposalDate": "2035-05-14"
}
  """.trimIndent()
