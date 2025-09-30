plugins {
    id("common")
    application
}

kotlin {
    jvmToolchain(21)
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
    testImplementation("io.kotest:kotest-assertions-core-jvm:6.0.3")
    testImplementation("io.mockk:mockk:1.14.5")
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = application.mainClass
        }

        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        archiveFileName.set("${project.name}-fat.jar")
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    }
    test {
        useJUnitPlatform()
        testLogging {
            showExceptions = true
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            events =
                setOf(
                    org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
                    org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
                    org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
                )
            showStandardStreams = true
        }
    }
}
