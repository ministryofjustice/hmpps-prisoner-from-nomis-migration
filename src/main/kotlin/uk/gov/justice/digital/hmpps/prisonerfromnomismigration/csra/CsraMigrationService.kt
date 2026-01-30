package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumberMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class CsraMigrationService(
  val nomisApiService: NomisApiService,
  val csraMappingService: CsraMappingService,
  val csraNomisApiService: CsraNomisApiService,
  objectMapper: ObjectMapper,
  @Value($$"${csra.page.size:1000}") pageSize: Long,
  @Value($$"${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${complete-check.count}") completeCheckCount: Int,
) : ByPageNumberMigrationService<PrisonerMigrationFilter, PrisonerId, CsraMappingDto>(
  mappingService = csraMappingService,
  migrationType = MigrationType.CSRA,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  objectMapper = objectMapper,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun getIds(
    migrationFilter: PrisonerMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ) = nomisApiService.getPrisonerIds(
    pageNumber = pageNumber,
    pageSize = pageSize,
  )

  override suspend fun getPageOfIds(
    migrationFilter: PrisonerMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): List<PrisonerId> = getIds(migrationFilter, pageSize, pageNumber).content

  override suspend fun getTotalNumberOfIds(migrationFilter: PrisonerMigrationFilter): Long = getIds(
    migrationFilter,
    1,
    0,
  ).totalElements

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonerId>) {
    TODO()
  }

  override suspend fun retryCreateMapping(context: MigrationContext<CsraMappingDto>) = createMappingOrOnFailureDo(
    context,
    context.body,
  ) {
    throw it
  }

  suspend fun createMappingOrOnFailureDo(
    context: MigrationContext<*>,
    mapping: CsraMappingDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      csraMappingService.createMapping(
        mapping,
        object :
          ParameterizedTypeReference<DuplicateErrorResponse<CsraMappingDto>>() {},
      )
    }.onFailure {
      failureHandler(it)
    }.onSuccess {
      TODO()
    }
  }

  override fun parseContextFilter(json: String): MigrationMessage<*, PrisonerMigrationFilter> = objectMapper
    .readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<PrisonerMigrationFilter, ByPageNumber>> = objectMapper
    .readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, PrisonerId> = objectMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, CsraMappingDto> = objectMapper.readValue(json)
}
