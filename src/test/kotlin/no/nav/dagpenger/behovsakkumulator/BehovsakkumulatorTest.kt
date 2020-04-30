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
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.shuffle
import io.kotest.property.arbitrary.subsequence
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import java.time.LocalDateTime
import no.nav.helse.rapids_rivers.testsupport.TestRapid

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
            100,
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
})

fun behovFor(behovId: String, vararg behov: String): JsonNode = //language=JSON
    objectMapper.readTree(
        """{
  "@id": "$behovId",
  "@opprettet": "${LocalDateTime.now()}",
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
