plugins {
    application
    alias(libs.plugins.spotless)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter)
    implementation(libs.jspecify)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
        target("src/**/*.yaml","src/**/*.yml")
        jackson()
    }
}
