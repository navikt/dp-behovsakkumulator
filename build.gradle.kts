import sun.jvmstat.monitor.MonitoredVmUtil.mainClass

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

val rapidsAndRiversVersion = "2026111908531795074794"

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    implementation("com.github.navikt.rapids-and-rivers:rapids-and-rivers-test:$rapidsAndRiversVersion")
    implementation("io.github.microutils:kotlin-logging:3.0.5")

    implementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-params:6.1.3")
    testImplementation("io.kotest:kotest-assertions-core-jvm:6.2.7")
    testImplementation("io.mockk:mockk:1.14.12")
}

application {
    applicationName = "dp-behovsakkumulator"
    mainClass.set("no.nav.dagpenger.behovsakkumulator.AppKt")
}
