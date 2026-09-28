import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    application
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.jspecify)
    implementation(libs.guava)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.archunit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // immutables
    annotationProcessor(libs.immutables.annotation.processor)
    compileOnly(libs.immutables.annotations)
    testCompileOnly(libs.immutables.annotations)

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
    mainClass = "lmarek.lcs.App"
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}

spotless {
    format("misc") {
        target("**/*.gradle.kts", "**/.gitignore", "**/*.toml")
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
    }
    java {
        removeUnusedImports()
        forbidWildcardImports()
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

// Heavy validation is explicit and separate from the normal regression suite.
val benchmark by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
}
configurations[benchmark.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
dependencies {
    add(benchmark.implementationConfigurationName, "org.openjdk.jmh:jmh-core:1.37")
    add(benchmark.annotationProcessorConfigurationName, "org.openjdk.jmh:jmh-generator-annprocess:1.37")
}
val stress by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[stress.implementationConfigurationName].extendsFrom(configurations.implementation.get())
dependencies {
    add(stress.implementationConfigurationName, "org.openjdk.jcstress:jcstress-core:0.16")
    add(stress.annotationProcessorConfigurationName, "org.openjdk.jcstress:jcstress-core:0.16")
}
tasks.named<JavaCompile>(benchmark.compileJavaTaskName) {
    options.errorprone { disable("NullAway"); disable("ThreadPriorityCheck") }
}
tasks.named<JavaCompile>(stress.compileJavaTaskName) {
    options.errorprone { disable("NullAway"); disable("UnusedVariable"); disable("ThreadPriorityCheck") }
}
tasks.register<JavaExec>("benchmarks") {
    group = "verification"
    description = "JMH: three forks, five warmup and five measurement iterations. Override with -PbenchmarkArgs."
    classpath = benchmark.runtimeClasspath
    mainClass = "org.openjdk.jmh.Main"
    args = providers.gradleProperty("benchmarkArgs").orElse("-f 3 -wi 5 -i 5 -w 1s -r 1s -prof gc -rf json -rff build/jmh-results.json").get().split(" ")
    providers.gradleProperty("shardingFixtureDirectory").orNull?.let {
        systemProperty("lcs.sharding.fixtureDirectory", it)
    }
    maxHeapSize = "32g"
}
tasks.register<JavaExec>("concurrencyStress") {
    group = "verification"
    description = "OpenJDK jcstress publication and subscriber protocol tests."
    classpath = stress.runtimeClasspath
    mainClass = "org.openjdk.jcstress.Main"
    args = providers.gradleProperty("stressArgs").orElse("-t lmarek.lcs.* -m quick -c 8 -r build/jcstress").get().split(" ")
}
tasks.register<JavaExec>("largePopulationSoak") {
    group = "verification"
    description = "Explicit 60-minute large-population soak under a 32 GiB heap, with JFR."
    classpath = benchmark.runtimeClasspath
    mainClass = "lmarek.lcs.xcs.LargePopulationSoak"
    maxHeapSize = "32g"
    jvmArgs("-XX:StartFlightRecording=filename=build/soak.jfr,settings=profile,dumponexit=true,maxsize=512m")
    args(providers.gradleProperty("soakSeconds").orElse("3600").get())
}
tasks.register<JavaExec>("shardingQuality") {
    group = "verification"
    description = "Train and compare shared and sharded XCS on paired held-out games."
    classpath = benchmark.runtimeClasspath
    mainClass = "lmarek.lcs.arena.ShardingQualityExperiment"
    maxHeapSize = "32g"
    args(
        providers.gradleProperty("shardingTrainingGames").orElse("1000").get(),
        providers.gradleProperty("shardingHeldOutGames").orElse("1000").get(),
        providers.gradleProperty("shardingWorkers").orElse("4").get(),
        providers.gradleProperty("shardingFixtureDirectory").orElse("build/sharding-quality-fixtures").get(),
    )
}
tasks.register<org.springframework.boot.gradle.tasks.run.BootRun>("performanceRun") {
    group = "application"
    description = "Arena launch profile with a 32 GiB heap; matching remains opt-in."
    mainClass = application.mainClass
    classpath = sourceSets.main.get().runtimeClasspath
    maxHeapSize = "32g"
}
