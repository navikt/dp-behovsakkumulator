import com.diffplug.spotless.LineEnding

plugins {
    kotlin("jvm") version "1.9.22"
    application
    id("com.diffplug.spotless") version "6.25.0"
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
    maven("https://jitpack.io")
    maven("https://packages.confluent.io/maven/")
}

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:2023120410321701682379.55cc1a24d803")
    implementation("io.github.microutils:kotlin-logging:3.0.5")

    implementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.6.2")
    testImplementation("io.mockk:mockk:1.13.7")
}

tasks {
    build {
        dependsOn("spotlessApply")
    }
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

spotless {
    kotlin {
        ktlint()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
    // https://github.com/diffplug/spotless/issues/1644
    lineEndings = LineEnding.PLATFORM_NATIVE // or any other except GIT_ATTRIBUTES
}
