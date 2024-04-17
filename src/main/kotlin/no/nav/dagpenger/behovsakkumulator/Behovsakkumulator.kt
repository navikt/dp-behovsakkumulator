package no.nav.dagpenger.behovsakkumulator

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import java.time.LocalDateTime

class Behovsakkumulator(rapidsConnection: RapidsConnection) : River.PacketListener {
    private companion object {
        private val log = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }

    private val behovUtenLøsning = mutableMapOf<String, Pair<MessageContext, JsonMessage>>()

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandKey("@behov")
                it.demandKey("@løsning")
                it.rejectKey("@final")
                it.requireKey("@id")
                it.interestedIn("@behovId")
                it.require("@opprettet", JsonNode::asLocalDateTime)

                // Ignorerer behov fra dp-quiz fra behovsakkumulator
                it.rejectValue("@event_name", "faktum_svar")
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        val behovId = packet["@behovId"].asText()

        withLoggingContext(
            "behovId" to behovId,
        ) {
            loggBehov(packet)
            val resultat =
                behovUtenLøsning[behovId]?.also { it.second.kombinerLøsninger(packet) } ?: (context to packet)

            if (resultat.second.erKomplett()) {
                resultat.second["@final"] = true
                resultat.second["@besvart"] = LocalDateTime.now().toString()

                loggLøstBehov()

                resultat.first.publish(resultat.second.toJson())
                behovUtenLøsning.remove(behovId)
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
                behovUtenLøsning[behovId] = resultat
            }
        }
    }

    private fun sendUfullstendigBehovEvent(pair: Pair<MessageContext, JsonMessage>) {
        val (context, behov) = pair
        val forventninger = behov.forventninger()
        val løsninger = behov.løsninger()
        val mangler = forventninger.minus(løsninger)

        loggUfullstendingBehov(mangler)

        val behovId = behov["@behovId"].asText()
        context.publish(
            behovId,
            JsonMessage.newMessage(
                "behov_uten_fullstendig_løsning",
                mapOf(
                    "behov_id" to behovId,
                    "behov_opprettet" to behov["@opprettet"].asLocalDateTime(),
                    "forventet" to forventninger,
                    "løsninger" to løsninger,
                    "mangler" to mangler,
                    "ufullstendig_behov" to behov.toJson(),
                ),
            ).toJson(),
        )
    }

    private fun JsonMessage.erKomplett(): Boolean = this.forventninger().all { it in this.løsninger() }

    private fun JsonMessage.forventninger(): Set<String> = this["@behov"].map(JsonNode::asText).toSet()

    private fun JsonMessage.løsninger(): Set<String> = this["@løsning"].fieldNames().asSequence().toSet()

    private fun JsonMessage.kombinerLøsninger(packet: JsonMessage) {
        val løsning = this["@løsning"] as ObjectNode
        packet["@løsning"].fields().forEach { (behovtype, delløsning) ->
            løsning.set<JsonNode>(behovtype, delløsning)
        }

        loggKombinering(this)
    }

    private fun loggBehov(packet: JsonMessage) {
        listOf(log, sikkerlogg).forEach { logger ->
            logger.info {
                val løsninger = packet["@løsning"].fieldNames().asSequence().joinToString(", ")
                "Mottok løsning for $løsninger"
            }
        }
    }

    private fun loggKombinering(packet: JsonMessage) {
        listOf(log, sikkerlogg).forEach { logger ->
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

    private fun loggLøstBehov() {
        listOf(log, sikkerlogg).forEach { logger ->
            logger.info {
                "Markert behov som final"
            }
        }
    }

    private fun loggUfullstendingBehov(mangler: Set<String>) {
        listOf(log, sikkerlogg).forEach { logger ->
            logger.error { "Mottok aldri løsning for ${mangler.joinToString { it }} innen 30 minutter." }
        }
    }
}
