package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiExtension.Companion.dpsContactPersonServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.CreateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest

@SpringAPIServiceTest
@Import(ContactPersonDpsApiService::class, ContactPersonConfiguration::class)
class ContactPersonDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: ContactPersonDpsApiService

  @Nested
  inner class CreatePerson {
    @Test
    internal fun `will pass oath2 token to contact endpoint`() = runTest {
      dpsContactPersonServer.stubCreatePerson()

      apiService.createPerson(createContactRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }
  }

  private fun createContactRequest(): CreateContactRequest = CreateContactRequest(
    firstName = "John",
    lastName = "Smith",
    createdBy = "QT12334",
  )
}
