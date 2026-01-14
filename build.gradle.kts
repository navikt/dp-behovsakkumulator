plugins {
    id("common")
    application
}

application {
    mainClass.set("no.nav.dagpenger.behovsakkumulator.AppKt")
    group = "no.nav.dagpenger"
    version = "1.0"
}

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    maven("https://packages.confluent.io/maven/")
}

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:2025110410541762250064.d7e58c3fad81")
    implementation("io.github.microutils:kotlin-logging:3.0.5")

    implementation("com.github.navikt.tbd-libs:rapids-and-rivers-test:2025.11.04-10.54-c831038e")
    implementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-params:6.0.2")
    testImplementation("io.kotest:kotest-assertions-core-jvm:6.0.7")
    testImplementation("io.mockk:mockk:1.14.7")
}

application {
    applicationName = "dp-behovsakkumulator"
    mainClass.set("no.nav.dagpenger.behovsakkumulator.AppKt")
}
