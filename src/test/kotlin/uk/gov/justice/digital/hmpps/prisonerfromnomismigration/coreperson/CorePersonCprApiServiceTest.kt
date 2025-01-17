package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonCprApiExtension.Companion.cprCorePersonServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonCprApiMockServer.Companion.migrateCoreRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest

@SpringAPIServiceTest
@Import(CorePersonCprApiService::class, CorePersonConfiguration::class)
class CorePersonCprApiServiceTest {
  @Autowired
  private lateinit var apiService: CorePersonCprApiService

  @Nested
  inner class MigrateCorePerson {
    @Test
    internal fun `will pass oath2 token to core endpoint`() = runTest {
      cprCorePersonServer.stubMigrateCorePerson()

      apiService.migrateCore(migrateCoreRequest())

      cprCorePersonServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the migrate endpoint`() = runTest {
      cprCorePersonServer.stubMigrateCorePerson()

      apiService.migrateCore(migrateCoreRequest())

      cprCorePersonServer.verify(
        postRequestedFor(urlPathEqualTo("/syscon-sync")),
      )
    }
  }
}
