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
    implementation("com.github.navikt:rapids-and-rivers:2026051812441779101082")
    implementation("io.github.microutils:kotlin-logging:3.0.5")

    implementation("com.github.navikt.tbd-libs:rapids-and-rivers-test:20260513.1819")
    implementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-params:6.1.0")
    testImplementation("io.kotest:kotest-assertions-core-jvm:6.1.11")
    testImplementation("io.mockk:mockk:1.14.11")
}

application {
    applicationName = "dp-behovsakkumulator"
    mainClass.set("no.nav.dagpenger.behovsakkumulator.AppKt")
}
