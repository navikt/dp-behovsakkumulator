package no.nav.dagpenger.behovsakkumulator

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe


import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDateTime
import java.util.UUID
import java.util.stream.Stream

internal class BehovsakkumulatorTest {
    private lateinit var rapid: TestRapid

    @BeforeEach
    fun setup() {
        rapid =
            TestRapid().apply {
                Behovsakkumulator(rapidsConnection = this)
            }
    }

    private companion object {
        private val muligeBehov =
            listOf(
                "Sykepengehistorikk",
                "AndreYtelser",
                "Foreldrepenger",
                "Verneplikt",
                "Arbeidssøker",
            )

        @JvmStatic
        private fun behovProvider() =
            Stream.of(
                Arguments.of(UUID.randomUUID(), muligeBehov.shuffled()),
                Arguments.of(
                    UUID.randomUUID(),
                    muligeBehov.shuffled().subList(2, 4),
                ),
            )
    }

    @Test
    fun `pakker med bare ett behov skal også akkumuleres`() {
        val behov = listOf("Dagpenger")
        behovFor(
            behovId = UUID.randomUUID().toString(),
            behov = behov.toTypedArray(),
        ).apply {
            rapid.sendTestMessage(this.toString())
        }.also {
            behov.forEach { behov: String ->
                rapid.sendTestMessage(it.medLøsning("""{ "$behov": [] }"""))
            }
        }

        with(rapid.inspektør) {
            size.shouldBe(1)
        }
    }

    @ParameterizedTest
    @MethodSource("behovProvider")
    fun `kombinere ett eller flere delsvar til et komplett svar`(
        behovId: UUID,
        genererteBehov: List<String>,
    ) {
        behovFor(
            behovId = behovId.toString(),
            behov = genererteBehov.toTypedArray(),
        ).apply {
            rapid.sendTestMessage(this.toString())
        }.also {
            genererteBehov.shuffled().forEach { behov: String ->
                rapid.sendTestMessage(it.medLøsning("""{ "$behov": [] }"""))
            }
        }

        with(rapid.inspektør) {
            size.shouldBe(1)
            field(0, "@behovId").asText() shouldBe behovId.toString()

            field(0, "@final").asBoolean() shouldBe true
            løsningerI(field(0, "@løsning")).containsAll(genererteBehov) shouldBe true

            assertDoesNotThrow { LocalDateTime.parse(field(0, "@besvart").asText()) }
        }

        rapid.reset()
    }

    @ParameterizedTest
    @MethodSource("behovProvider")
    fun `sende ufullstending event ved mangel av løsninger på behov`(
        behovId: UUID,
        genererteBehov: List<String>,
    ) {
        behovFor(
            behovId = behovId.toString(),
            behov = genererteBehov.toTypedArray(),
            opprettet = LocalDateTime.now().minusHours(1),
        ).apply {
            rapid.sendTestMessage(this.toString())
        }.also {
            genererteBehov.first().let { behov: String ->
                rapid.sendTestMessage(it.medLøsning("""{ "$behov": [] }"""))
            }
        }

        with(rapid.inspektør) {
            if (size == 1 && message(0)["@event_name"] != null) {
                field(0, "@event_name").asText() shouldBe "behov_uten_fullstendig_løsning"
                field(0, "@opprettet").asLocalDateTime() shouldNotBe null
                field(0, "forventet").asIterable().toList().shouldNotBeEmpty()
                field(0, "mangler").asIterable().toList().shouldNotBeEmpty()
                field(0, "behov_opprettet").asText() shouldNotBe null
                field(0, "ufullstendig_behov").asText() shouldNotBe null
            }
        }

        rapid.reset()
    }
}

@Language("JSON")
fun behovFor(
    behovId: String,
    vararg behov: String,
    opprettet: LocalDateTime = LocalDateTime.now(),
): JsonNode =
    objectMapper.readTree(
        """
        {
          "@id": "${UUID.randomUUID()}",
          "@behovId": "$behovId",
          "@opprettet": "$opprettet",
          "@behov": [
            ${behov.joinToString { "\"${it}\"" }}
          ]
        }
        """.trimIndent(),
    )

private fun JsonNode.medLøsning(løsning: String) =
    (this.deepCopy() as ObjectNode).set<ObjectNode>("@løsning", objectMapper.readTree(løsning))
        .toString()

private fun løsningerI(løsning: JsonNode): List<String> =
    løsning.fields().asSequence().toList().map {
        it.key
    }

private val objectMapper: ObjectMapper =
    jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(JavaTimeModule())
