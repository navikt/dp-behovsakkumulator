package no.nav.dagpenger.behovsakkumulator

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.shuffle
import io.kotest.property.arbitrary.subsequence
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import java.time.LocalDateTime

internal class BehovsakkumulatorTest : ShouldSpec({
    lateinit var rapid: TestRapid
    val muligeBehov = listOf(
        "Sykepengehistorikk",
        "AndreYtelser",
        "Foreldrepenger",
        "Verneplikt",
        "Arbeidssøker"
    )

    beforeTest {
        rapid = TestRapid().apply {
            Behovsakkumulator(rapidsConnection = this)
        }
    }

    should("kombinere ett eller flere delsvar til et komplett svar") {
        checkAll(
            muligeBehov.size,
            Arb.uuid(),
            Arb.choice(
                Arb.shuffle(muligeBehov),
                Arb.subsequence(muligeBehov)
            )
        ) { behovId, genererteBehov ->
            // Skip empty lists
            if (genererteBehov.isEmpty()) return@checkAll

            behovFor(
                behovId = behovId.toString(),
                behov = *genererteBehov.toTypedArray()
            ).apply {
                rapid.sendTestMessage(this.toString())
            }.also {
                genererteBehov.shuffled().forEach { behov: String ->
                    rapid.sendTestMessage(it.medLøsning("""{ "$behov": [] }"""))
                }
            }

            with(rapid.inspektør) {
                size.shouldBeExactly(1)

                field(0, "@id").asText().shouldBe(behovId.toString())
                field(0, "@final").asBoolean().shouldBeTrue()
                løsningerI(field(0, "@løsning")).shouldContainAll(genererteBehov)

                shouldNotThrowAny { LocalDateTime.parse(field(0, "@besvart").asText()) }
            }

            rapid.reset()
        }
    }

    should("sende ufullstending event ved mangel av løsninger på behov") {
        checkAll(
            muligeBehov.size,
            Arb.uuid(),
            Arb.choice(
                Arb.shuffle(muligeBehov),
                Arb.subsequence(muligeBehov)
            )
        ) { behovId, genererteBehov ->
            // Skip empty lists
            if (genererteBehov.isEmpty()) return@checkAll

            behovFor(
                behovId = behovId.toString(),
                behov = *genererteBehov.toTypedArray(),
                opprettet = LocalDateTime.now().minusHours(1) // Manipulate time to trigger 30 minute threshold
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
})

fun behovFor(behovId: String, vararg behov: String, opprettet: LocalDateTime = LocalDateTime.now()): JsonNode = //language=JSON
    objectMapper.readTree(
        """{
  "@id": "$behovId",
  "@opprettet": "$opprettet",
  "vedtakId": "id",
  "@behov": [  ${behov.joinToString { "\"${it}\"" }}  ]
}
"""
    )

private fun JsonNode.medLøsning(løsning: String) =
    (this.deepCopy() as ObjectNode).set<ObjectNode>("@løsning", objectMapper.readTree(løsning))
        .toString()

private fun løsningerI(løsning: JsonNode): List<String> =
    løsning.fields().asSequence().toList().map {
        it.key
    }

private val objectMapper: ObjectMapper = jacksonObjectMapper()
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .registerModule(JavaTimeModule())
