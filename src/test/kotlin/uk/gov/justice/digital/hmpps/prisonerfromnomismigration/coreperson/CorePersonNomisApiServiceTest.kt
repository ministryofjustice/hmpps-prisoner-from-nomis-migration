package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePersonAddressContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CreateOffenderEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CreateOffenderEmailResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.UpdateOffenderEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDateTime

@ExtendWith(NomisApiExtension::class)
@SpringAPIServiceTest
@Import(CorePersonNomisApiService::class, CorePersonNomisApiMockServer::class)
class CorePersonNomisApiServiceTest(
  @Autowired private val apiService: CorePersonNomisApiService,
  @Autowired private val mockServer: CorePersonNomisApiMockServer,
) {
  @Nested
  inner class GetPerson {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubGetCorePerson(prisonNumber = "A12345BC")

      apiService.getCorePerson(nomisPrisonNumber = "A12345BC")

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetCorePerson(prisonNumber = "A12345BC")

      apiService.getCorePerson(nomisPrisonNumber = "A12345BC")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/core-person/A12345BC")),
      )
    }

    @Test
    fun `will return the core person`() = runTest {
      mockServer.stubGetCorePerson(prisonNumber = "A12345BC", corePerson = corePerson().copy(offenders = listOf(corePerson().offenders!![0].copy(lastName = "Smith"))))

      val corePerson = apiService.getCorePerson(nomisPrisonNumber = "A12345BC")

      assertThat(corePerson.offenders!![0].lastName).isEqualTo("Smith")
    }

    @Test
    fun `will throw error when person does not exist`() = runTest {
      mockServer.stubGetCorePerson(prisonNumber = "A12345BC", status = HttpStatus.NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getCorePerson(nomisPrisonNumber = "A12345BC")
      }
    }
  }

  @Nested
  inner class GetPersonAddressesAndContacts {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubGetAddressesAndContacts(prisonNumber = "A12345BC")

      apiService.getCorePersonAddressesAndContacts(nomisPrisonNumber = "A12345BC")

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetAddressesAndContacts(prisonNumber = "A12345BC")

      apiService.getCorePersonAddressesAndContacts(nomisPrisonNumber = "A12345BC")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/core-person/A12345BC/addresses-contacts")),
      )
    }

    @Test
    fun `will return the addresses and contacts`() = runTest {
      mockServer.stubGetAddressesAndContacts(
        prisonNumber = "A12345BC",
        addressesAndContacts = CorePersonAddressContact(
          addresses = listOf(
            OffenderAddress(
              addressId = 12345,
              primaryAddress = true,
              mailAddress = true,
              createdDateTime = LocalDateTime.parse("2024-06-01T12:00:00"),
              createdByUsername = "billybob",
              lastUpdatedDateTime = null,
              lastUpdatedByUsername = null,
            ),
          ),
        ),
      )

      val addressesAndContacts = apiService.getCorePersonAddressesAndContacts(nomisPrisonNumber = "A12345BC")

      assertThat(addressesAndContacts.addresses!![0].addressId).isEqualTo(12345)
    }

    @Test
    fun `will throw error when person does not exist`() = runTest {
      mockServer.stubGetAddressesAndContacts(prisonNumber = "A12345BC", status = HttpStatus.NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getCorePersonAddressesAndContacts(nomisPrisonNumber = "A12345BC")
      }
    }
  }

  @Nested
  inner class CreateOffenderEmail {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubCreateOffenderEmail(offenderId = 12345)

      apiService.createOffenderEmail(offenderId = 12345, createOffenderEmailRequest())

      mockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass request to service`() = runTest {
      mockServer.stubCreateOffenderEmail(offenderId = 12345)

      apiService.createOffenderEmail(offenderId = 12345, createOffenderEmailRequest())

      mockServer.verify(
        postRequestedFor(anyUrl())
          .withRequestBodyJsonPath("email", equalTo("test@example.com")),
      )
    }

    @Test
    fun `will pass NOMIS offender id to service`() = runTest {
      mockServer.stubCreateOffenderEmail(offenderId = 12345)

      apiService.createOffenderEmail(offenderId = 12345, createOffenderEmailRequest())

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/core-person/12345/email")),
      )
    }

    @Test
    fun `will return the created email id`() = runTest {
      mockServer.stubCreateOffenderEmail(
        offenderId = 12345,
        response = CreateOffenderEmailResponse(emailAddressId = 45678),
      )

      val response = apiService.createOffenderEmail(offenderId = 12345, createOffenderEmailRequest())

      assertThat(response.emailAddressId).isEqualTo(45678)
    }

    @Test
    fun `will throw error when create fails`() = runTest {
      mockServer.stubCreateOffenderEmail(offenderId = 12345, status = HttpStatus.BAD_REQUEST)

      assertThrows<WebClientResponseException.BadRequest> {
        apiService.createOffenderEmail(offenderId = 12345, createOffenderEmailRequest())
      }
    }
  }

  @Nested
  inner class GetOffenderEmail {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubGetOffenderEmail(offenderId = 12345, emailAddressId = 45678)

      apiService.getOffenderEmail(offenderId = 12345, emailAddressId = 45678)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS ids to service`() = runTest {
      mockServer.stubGetOffenderEmail(offenderId = 12345, emailAddressId = 45678)

      apiService.getOffenderEmail(offenderId = 12345, emailAddressId = 45678)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/core-person/12345/email/45678")),
      )
    }

    @Test
    fun `will return the email`() = runTest {
      mockServer.stubGetOffenderEmail(
        offenderId = 12345,
        emailAddressId = 45678,
        emailAddress = offenderEmailAddress(45678).copy(email = "updated@example.com"),
      )

      val email = apiService.getOffenderEmail(offenderId = 12345, emailAddressId = 45678)

      assertThat(email.emailAddressId).isEqualTo(45678)
      assertThat(email.email).isEqualTo("updated@example.com")
    }

    @Test
    fun `will throw error when email does not exist`() = runTest {
      mockServer.stubGetOffenderEmail(offenderId = 12345, emailAddressId = 45678, status = HttpStatus.NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getOffenderEmail(offenderId = 12345, emailAddressId = 45678)
      }
    }
  }

  @Nested
  inner class UpdateOffenderEmail {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubUpdateOffenderEmail(offenderId = 12345, emailAddressId = 45678)

      apiService.updateOffenderEmail(offenderId = 12345, emailAddressId = 45678, updateOffenderEmailRequest())

      mockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass request to service`() = runTest {
      mockServer.stubUpdateOffenderEmail(offenderId = 12345, emailAddressId = 45678)

      apiService.updateOffenderEmail(offenderId = 12345, emailAddressId = 45678, updateOffenderEmailRequest())

      mockServer.verify(
        putRequestedFor(anyUrl())
          .withRequestBodyJsonPath("email", equalTo("test@example.com")),
      )
    }

    @Test
    fun `will pass NOMIS ids to service`() = runTest {
      mockServer.stubUpdateOffenderEmail(offenderId = 12345, emailAddressId = 45678)

      apiService.updateOffenderEmail(offenderId = 12345, emailAddressId = 45678, updateOffenderEmailRequest())

      mockServer.verify(
        putRequestedFor(urlPathEqualTo("/core-person/12345/email/45678")),
      )
    }

    @Test
    fun `will throw error when update fails`() = runTest {
      mockServer.stubUpdateOffenderEmail(offenderId = 12345, emailAddressId = 45678, status = HttpStatus.BAD_REQUEST)

      assertThrows<WebClientResponseException.BadRequest> {
        apiService.updateOffenderEmail(offenderId = 12345, emailAddressId = 45678, updateOffenderEmailRequest())
      }
    }
  }

  @Nested
  inner class DeleteOffenderEmail {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubDeleteOffenderEmail(offenderId = 12345, emailAddressId = 45678)

      apiService.deleteOffenderEmail(offenderId = 12345, emailAddressId = 45678)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS ids to service`() = runTest {
      mockServer.stubDeleteOffenderEmail(offenderId = 12345, emailAddressId = 45678)

      apiService.deleteOffenderEmail(offenderId = 12345, emailAddressId = 45678)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/core-person/12345/email/45678")),
      )
    }

    @Test
    fun `will throw error when delete fails`() = runTest {
      mockServer.stubDeleteOffenderEmail(offenderId = 12345, emailAddressId = 45678, status = HttpStatus.BAD_REQUEST)

      assertThrows<WebClientResponseException.BadRequest> {
        apiService.deleteOffenderEmail(offenderId = 12345, emailAddressId = 45678)
      }
    }
  }

  fun createOffenderEmailRequest() = CreateOffenderEmailRequest(email = "test@example.com")

  fun updateOffenderEmailRequest() = UpdateOffenderEmailRequest(email = "test@example.com")
}
