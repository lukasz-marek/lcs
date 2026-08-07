import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    application
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter)
    implementation(libs.jspecify)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.archunit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // errorprone
    errorprone(libs.errorprone)
    errorprone(libs.nullaway)
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    // Define the main class for the application.
    mainClass = "org.example.App"
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}

spotless {
    format("misc") {
        target("**/*.gradle.kts", "**/.gitignore")
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
    }
    java {
        removeUnusedImports()
        expandWildcardImports()
        googleJavaFormat()
    }
    json {
        target("src/**/*.json")
        jackson()
    }
    yaml {
        target("src/**/*.yaml", "src/**/*.yml")
        jackson()
    }
}

tasks.withType<JavaCompile> {
    options.errorprone {
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:OnlyNullMarked", true)
        if (name.lowercase().contains("test")) {
            options.errorprone {
                disable("NullAway")
            }
        }
    }
}
