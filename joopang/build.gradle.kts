import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.allopen) apply false
    alias(libs.plugins.kotlin.noarg) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

val libsCatalog = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
val springCloudBom = libsCatalog.findLibrary("spring.cloud.dependencies").orElseThrow().get().toString()

allprojects {
    group = property("app.group").toString()
    version = property("app.version")
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.jetbrains.kotlin.plugin.allopen")
    apply(plugin = "org.jetbrains.kotlin.plugin.noarg")
    apply(plugin = "io.spring.dependency-management")

    configureAllOpenAnnotations()
    configureNoArgAnnotations()

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = JavaVersion.VERSION_17.toString()
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    extensions.configure(DependencyManagementExtension::class.java) {
        imports {
            mavenBom(springCloudBom)
        }
    }
}

fun Project.configureAllOpenAnnotations() {
    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.allopen") {
        val extension = extensions.findByName("allOpen") ?: return@withPlugin
        val annotationMethod = extension.javaClass.getMethod("annotation", String::class.java)
        listOf(
            "jakarta.persistence.Entity",
            "jakarta.persistence.Embeddable",
            "jakarta.persistence.MappedSuperclass",
            "javax.persistence.Entity",
            "javax.persistence.Embeddable",
            "javax.persistence.MappedSuperclass",
        ).forEach { annotation ->
            annotationMethod.invoke(extension, annotation)
        }
    }
}

fun Project.configureNoArgAnnotations() {
    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.noarg") {
        val extension = extensions.findByName("kotlinNoArg") ?: return@withPlugin
        val annotationMethod = extension.javaClass.getMethod("annotation", String::class.java)
        listOf(
            "jakarta.persistence.Entity",
            "jakarta.persistence.Embeddable",
            "jakarta.persistence.MappedSuperclass",
            "javax.persistence.Entity",
            "javax.persistence.Embeddable",
            "javax.persistence.MappedSuperclass",
        ).forEach { annotation ->
            annotationMethod.invoke(extension, annotation)
        }
        runCatching {
            val setter = extension.javaClass.getMethod("setInvokeInitializers", Boolean::class.javaPrimitiveType)
            setter.invoke(extension, false)
        }
    }
}
