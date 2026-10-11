import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway

plugins {
    java
    id("io.quarkus")
    id("net.ltgt.errorprone") version ("5.1.0")
    id("net.ltgt.nullaway") version ("3.0.0")
}

group = "com.faforever"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    errorprone("com.uber.nullaway:nullaway:0.13.4")
    errorprone("com.google.errorprone:error_prone_core:2.49.0")

    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:${property("quarkusPlatformVersion")}"))
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation("io.quarkus:quarkus-messaging-rabbitmq")
    implementation("io.quarkus:quarkus-websockets-next")
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-jackson")
    implementation("io.quarkus:quarkus-hibernate-orm-panache")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-smallrye-jwt")
    implementation("io.quarkus:quarkus-smallrye-jwt-build")
    implementation("io.quarkus:quarkus-logging-json")
    implementation("io.quarkus:quarkus-rest-client-jackson")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    implementation("com.networknt:json-schema-validator:3.0.8")

    val jsonSchemaVersion = "5.0.0"
    implementation("com.github.victools:jsonschema-generator:$jsonSchemaVersion")
    implementation("com.github.victools:jsonschema-module-jackson:$jsonSchemaVersion")

    val lombokVersion = "1.18.42"
    implementation("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testImplementation("io.quarkus:quarkus-junit")
    testImplementation("io.quarkus:quarkus-junit-component")
    testImplementation("io.quarkus:quarkus-junit-mockito")
    testImplementation("io.quarkus:quarkus-panache-mock")
    testImplementation("io.smallrye.reactive:smallrye-reactive-messaging-in-memory")
    testImplementation("org.hamcrest:hamcrest")
}

group = "com.faforever.server"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

nullaway {
    jspecifyMode = true
    onlyNullMarked = true
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
    options.errorprone {
        disableAllChecks = true
        nullaway {
            error()
            assertsEnabled = true
            treatGeneratedAsUnannotated = true
            excludedFieldAnnotations.add("io.quarkus.test.InjectMock")
            excludedFieldAnnotations.add("io.quarkus.test.junit.mockito.InjectSpy")
        }
    }
}
