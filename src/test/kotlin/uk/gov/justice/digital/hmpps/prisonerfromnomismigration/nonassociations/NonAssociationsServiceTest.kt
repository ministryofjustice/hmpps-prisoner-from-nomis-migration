package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations.model.UpsertSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NonAssociationsApiExtension.Companion.nonAssociationsApi
import java.time.LocalDate

private const val NON_ASSOCIATION_ID = 72345L

@SpringAPIServiceTest
@Import(NonAssociationsService::class, NonAssociationsConfiguration::class)
internal class NonAssociationsServiceTest {

  @Autowired
  private lateinit var nonAssociationsService: NonAssociationsService

  @Nested
  @DisplayName("PUT /sync/upsert")
  inner class CreateNonAssociationForSynchronisation {
    @BeforeEach
    internal fun setUp() {
      nonAssociationsApi.stubUpsertNonAssociationForSynchronisation(nonAssociationId = NON_ASSOCIATION_ID)
      runBlocking {
        nonAssociationsService.upsertNonAssociation(
          UpsertSyncRequest(
            firstPrisonerNumber = "A1234CD",
            firstPrisonerReason = UpsertSyncRequest.FirstPrisonerReason.VIC,
            secondPrisonerNumber = "E5678EF",
            secondPrisonerReason = UpsertSyncRequest.SecondPrisonerReason.PER,
            restrictionType = UpsertSyncRequest.RestrictionType.CELL,
            comment = "Do not keep together - fighting",
            authorisedBy = "Jim Smith",
            effectiveFromDate = LocalDate.parse("2022-01-01"),
            expiryDate = LocalDate.parse("2022-07-01"),
          ),
        )
      }
    }

    @Test
    fun `should call api with OAuth2 token`() {
      nonAssociationsApi.verify(
        putRequestedFor(urlEqualTo("/sync/upsert"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass data to the api`() {
      nonAssociationsApi.verify(
        putRequestedFor(urlEqualTo("/sync/upsert"))
          .withRequestBody(matchingJsonPath("firstPrisonerNumber", WireMock.equalTo("A1234CD")))
          .withRequestBody(matchingJsonPath("firstPrisonerReason", WireMock.equalTo("VIC")))
          .withRequestBody(matchingJsonPath("secondPrisonerNumber", WireMock.equalTo("E5678EF")))
          .withRequestBody(matchingJsonPath("secondPrisonerReason", WireMock.equalTo("PER")))
          .withRequestBody(matchingJsonPath("restrictionType", WireMock.equalTo("CELL")))
          .withRequestBody(matchingJsonPath("comment", WireMock.equalTo("Do not keep together - fighting")))
          .withRequestBody(matchingJsonPath("authorisedBy", WireMock.equalTo("Jim Smith")))
          .withRequestBody(matchingJsonPath("effectiveFromDate", WireMock.equalTo("2022-01-01")))
          .withRequestBody(matchingJsonPath("expiryDate", WireMock.equalTo("2022-07-01"))),
      )
    }
  }

  @Nested
  @DisplayName("DELETE /sync")
  inner class DeleteNonAssociationForSynchronisation {
    @Nested
    inner class NonAssociationExists {
      @BeforeEach
      internal fun setUp() {
        nonAssociationsApi.stubDeleteNonAssociationForSynchronisation(nonAssociationId = NON_ASSOCIATION_ID)
        runBlocking {
          nonAssociationsService.deleteNonAssociation(nonAssociationId = NON_ASSOCIATION_ID)
        }
      }

      @Test
      fun `should call api with OAuth2 token`() {
        nonAssociationsApi.verify(
          deleteRequestedFor(urlEqualTo("/sync/delete/$NON_ASSOCIATION_ID"))
            .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
        )
      }
    }

    @Nested
    inner class NonAssociationAlreadyDeleted {
      @BeforeEach
      internal fun setUp() {
        nonAssociationsApi.stubDeleteNonAssociationForSynchronisationNotFound(nonAssociationId = NON_ASSOCIATION_ID)
      }

      @Test
      fun `should ignore 404 error`() {
        runBlocking {
          nonAssociationsService.deleteNonAssociation(nonAssociationId = NON_ASSOCIATION_ID)
        }
      }
    }
  }
}