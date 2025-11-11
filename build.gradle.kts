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
    implementation("com.github.navikt:rapids-and-rivers:2025061811051750237542.df739400e55e")
    implementation("io.github.microutils:kotlin-logging:3.0.5")

    implementation("com.github.navikt.tbd-libs:rapids-and-rivers-test:2025.06.20-13.05-40af2647")
    implementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.14.0")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.9.1")
    testImplementation("io.mockk:mockk:1.14.5")
}

application {
    applicationName = "dp-behovsakkumulator"
    mainClass.set("no.nav.dagpenger.behovsakkumulator.AppKt")
}
