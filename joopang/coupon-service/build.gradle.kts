import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin

plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    id("jacoco")
}

dependencies {
    implementation(project(":common"))

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation(libs.spring.kafka)
    implementation(libs.redisson.client)
    implementation(libs.jackson.kotlin)

    runtimeOnly(libs.mysql.connector)

    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.test.containers.mysql)
    testImplementation(libs.test.containers.junit.jupiter)
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.kotest:kotest-property:5.8.0")
    testImplementation(libs.spring.kafka.test)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    if (System.getProperty("spring.profiles.active").isNullOrBlank()) {
        systemProperty("spring.profiles.active", "test")
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

val testSourceSet = extensions.getByType(SourceSetContainer::class.java).named("test")

tasks.register<Test>("unitTest") {
    description = "Runs unit tests only (excludes integration tests)."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = testSourceSet.get().output.classesDirs
    classpath = testSourceSet.get().runtimeClasspath
    useJUnitPlatform {
        excludeTags("integration")
    }
    if (System.getProperty("spring.profiles.active").isNullOrBlank()) {
        systemProperty("spring.profiles.active", "test")
    }
}

tasks.register<Test>("integrationTest") {
    description = "Runs only integration tests tagged with @Tag(\"integration\")."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = testSourceSet.get().output.classesDirs
    classpath = testSourceSet.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("integration")
    }
    if (System.getProperty("spring.profiles.active").isNullOrBlank()) {
        systemProperty("spring.profiles.active", "test")
    }
}
