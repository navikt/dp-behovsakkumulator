package no.nav.dagpenger.behovsakkumulator

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.LocalDateTime
import java.util.UUID
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime

private val log = KotlinLogging.logger {}
private val sikkerLogg = KotlinLogging.logger("tjenestekall")

class Behovsakkumulator(rapidsConnection: RapidsConnection) : River.PacketListener {
    private val behovUtenLøsning = mutableMapOf<String, Pair<RapidsConnection.MessageContext, JsonMessage>>()

    init {
        River(rapidsConnection).apply {
            validate { it.forbid("@final") }
            validate { it.requireKey("@id", "@behov", "@løsning", "vedtakId") }
            validate { it.require("@opprettet", JsonNode::asLocalDateTime) }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        loggBehov(packet)
        val id = packet["@id"].asText()
        val resultat = behovUtenLøsning[id]?.also { it.second.kombinerLøsninger(packet) } ?: (context to packet)

        if (resultat.second.erKomplett()) {
            resultat.second["@final"] = true
            resultat.second["@besvart"] = LocalDateTime.now().toString()

            loggLøstBehov(resultat.second)

            resultat.first.send(resultat.second.toJson())
            behovUtenLøsning.remove(id)
        } else {
            behovUtenLøsning
                .filterValues { (_, packet) ->
                    packet["@opprettet"].asLocalDateTime().isBefore(LocalDateTime.now().minusMinutes(30))
                }
                .forEach { (key, _) ->
                    behovUtenLøsning.remove(key).also {
                        if (it != null) {
                            sendUfullstendigBehovEvent(it)
                        }
                    }
                }
            behovUtenLøsning[id] = resultat
        }
    }

    private fun sendUfullstendigBehovEvent(pair: Pair<RapidsConnection.MessageContext, JsonMessage>) {

        val (context, behov) = pair
        val forventninger = behov.forventninger()
        val løsninger = behov.løsninger()
        val mangler = forventninger.minus(løsninger)
        loggUfullstendingBehov(behov, mangler)
        val behovId = behov["@id"].asText()
        context.send(
            behovId, JsonMessage.newMessage(
                mapOf(
                    "@event_name" to "behov_uten_fullstendig_løsning",
                    "@id" to UUID.randomUUID(),
                    "@opprettet" to LocalDateTime.now(),
                    "behov_id" to behovId,
                    "behov_opprettet" to behov["@opprettet"].asLocalDateTime(),
                    "forventet" to forventninger,
                    "løsninger" to løsninger,
                    "mangler" to mangler,
                    "ufullstendig_behov" to behov.toJson()
                )
            ).toJson()
        )
    }

    private fun JsonMessage.erKomplett(): Boolean = this.forventninger().all { it in this.løsninger() }

    private fun JsonMessage.forventninger(): List<String> = this["@behov"].map(JsonNode::asText)

    private fun JsonMessage.løsninger(): List<String> = this["@løsning"].fieldNames().asSequence().toList()

    private fun JsonMessage.kombinerLøsninger(packet: JsonMessage) {
        val løsning = this["@løsning"] as ObjectNode
        packet["@løsning"].fields().forEach { (behovtype, delløsning) ->
            løsning.set<JsonNode>(behovtype, delløsning)
        }

        loggKombinering(this)
    }

    private fun loggBehov(packet: JsonMessage) {
        withLoggingContext(
            "behovId" to packet["@id"].asText(),
            "vedtakId" to packet["vedtakId"].asText()
        ) {
            listOf(log, sikkerLogg).forEach { logger ->
                logger.info {
                    val løsninger = packet["@løsning"].fieldNames().asSequence().joinToString(", ")

                    "Mottok løsning for $løsninger"
                }
            }
        }
    }

    private fun loggKombinering(packet: JsonMessage) {
        withLoggingContext(
            "behovId" to packet["@id"].asText(),
            "vedtakId" to packet["vedtakId"].asText()
        ) {
            listOf(log, sikkerLogg).forEach { logger ->
                logger.info {
                    val løsninger = packet["@løsning"].fieldNames().asSequence().toList()
                    val behov = packet["@behov"].map(JsonNode::asText)
                    val mangler = behov.minus(løsninger)
                    val melding = "Har løsninger for [${løsninger.joinToString(", \n\t", "\n\t", "\n")}]. "

                    if (mangler.isEmpty()) return@info "Ferdig! $melding"

                    melding + "Venter på løsninger for [${mangler.joinToString(", \n\t", "\n\t", "\n")}]"
                }
            }
        }
    }

    private fun loggLøstBehov(packet: JsonMessage) {
        withLoggingContext(
            "behovId" to packet["@id"].asText(),
            "vedtakId" to packet["vedtakId"].asText()
        ) {
            listOf(log, sikkerLogg).forEach { logger ->
                logger.info {
                    "Markert behov som final"
                }
            }
        }
    }

    private fun loggUfullstendingBehov(packet: JsonMessage, mangler: List<String>) {
        withLoggingContext(
            "behovId" to packet["@id"].asText(),
            "vedtakId" to packet["vedtakId"].asText()
        ) {
            listOf(log, sikkerLogg).forEach { logger ->
                logger.error {
                    "Mottok aldri løsning for ${mangler.joinToString { it }} innen 30 minutter."
                }
            }
        }
    }
}
