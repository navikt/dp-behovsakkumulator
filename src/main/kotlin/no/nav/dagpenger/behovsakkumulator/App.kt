package no.nav.dagpenger.behovsakkumulator

import no.nav.helse.rapids_rivers.RapidApplication

fun main() {
    RapidApplication.create(System.getenv()).apply {
        Behovsakkumulator(this)
    }.start()
}
