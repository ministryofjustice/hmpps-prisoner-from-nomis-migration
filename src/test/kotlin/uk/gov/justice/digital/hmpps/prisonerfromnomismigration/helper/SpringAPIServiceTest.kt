package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper

import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.autoconfigure.http.codec.CodecsAutoConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTestContextBootstrapper
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.BootstrapWith
import org.springframework.web.reactive.config.EnableWebFlux
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ActivitiesApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.AdjudicationsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.IncidentsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.LocationsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.SentencingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.VisitsApiExtension
import uk.gov.justice.hmpps.kotlin.auth.HmppsReactiveWebClientConfiguration
import java.lang.annotation.Inherited
import kotlin.annotation.AnnotationTarget.ANNOTATION_CLASS
import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Annotation for an API service test that focuses **only** on services that call a WebClient
 *
 *
 * Using this annotation will disable full auto-configuration and instead apply only
 *
 */
@Target(ANNOTATION_CLASS, CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Inherited
@ExtendWith(
  NomisApiExtension::class,
  HmppsAuthApiExtension::class,
  VisitsApiExtension::class,
  MappingApiExtension::class,
  SentencingApiExtension::class,
  ActivitiesApiExtension::class,
  AdjudicationsApiExtension::class,
  IncidentsApiExtension::class,
  LocationsApiExtension::class,
  AlertsDpsApiExtension::class,
)
@ActiveProfiles("test")
@EnableWebFlux
@EnableWebFluxSecurity
@SpringBootTest(classes = [JacksonAutoConfiguration::class, CodecsAutoConfiguration::class, WebClientConfiguration::class, WebClientAutoConfiguration::class, ReactiveSecurityAutoConfiguration::class, ReactiveOAuth2ClientAutoConfiguration::class, HmppsReactiveWebClientConfiguration::class])
@BootstrapWith(SpringBootTestContextBootstrapper::class)
annotation class SpringAPIServiceTest
