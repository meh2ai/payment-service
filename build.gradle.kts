plugins {
    java
    groovy
    jacoco
    id("org.springframework.boot") version "3.5.8"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.17.0"
    id("com.github.spotbugs") version "6.4.6"
    id("io.freefair.lombok") version "9.1.0"
    id("com.google.cloud.tools.jib") version "3.5.1"
    application
}

group = "com.payment"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass.set("com.payment.PaymentServiceApplication")
}

jib {
    from {
        image = "eclipse-temurin:21-jre-alpine"
    }
    to {
        image = "payment-service"
    }
}

repositories {
    mavenCentral()
}

val versions = mapOf(
    "swagger" to "2.2.41",
    "spock" to "2.4-M7-groovy-4.0",
    "groovy" to "4.0.29",
    "testcontainers" to "1.21.3",
    "modelmapper" to "3.2.6",
    "lombok" to "1.18.42",
    "awaitility" to "4.3.0",
    "temporal" to "1.32.1"
)

dependencies {

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.kafka:spring-kafka")

    implementation("org.liquibase:liquibase-core")
    runtimeOnly("org.postgresql:postgresql")

    implementation("org.modelmapper:modelmapper:${versions["modelmapper"]}")

    implementation("io.temporal:temporal-spring-boot-starter:${versions["temporal"]}")

    implementation("io.swagger.core.v3:swagger-annotations:${versions["swagger"]}")
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("org.openapitools:jackson-databind-nullable:0.2.8")

    testImplementation("org.apache.groovy:groovy:${versions["groovy"]}")
    testImplementation("org.apache.groovy:groovy-json:${versions["groovy"]}")

    testImplementation("io.temporal:temporal-testing:${versions["temporal"]}")

    testImplementation("org.spockframework:spock-core:${versions["spock"]}")
    testImplementation("org.spockframework:spock-spring:${versions["spock"]}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.awaitility:awaitility:${versions["awaitility"]}")
    testImplementation("org.testcontainers:spock:${versions["testcontainers"]}")
    testImplementation("org.testcontainers:postgresql:${versions["testcontainers"]}")
    testImplementation("org.testcontainers:kafka:${versions["testcontainers"]}")
}

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("${rootDir}/api/openapi.yaml")
    outputDir.set(layout.buildDirectory.dir("generated").get().asFile.absolutePath)
    apiPackage.set("com.payment.api")
    modelPackage.set("com.payment.api.model")
    configOptions.set(
        mapOf(
            "documentationProvider" to "source",
            "interfaceOnly" to "true",
            "useSpringBoot3" to "true",
            "useJakartaEe" to "true",
            "openApiNullable" to "true",
            "useTags" to "true"
        )
    )
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated/src/main/java"))
        }
    }
}

tasks.compileJava {
    dependsOn(tasks.openApiGenerate)
}

spotbugs {
    ignoreFailures.set(false)
    excludeFilter.set(file("config/spotbugs-exclude.xml"))
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports {
        create("html") { required.set(true) }
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.14"
}


val jacocoExcludedPatterns = listOf(
    "**/generated/**",
    "org/openapitools/**",
    "**/api/**",
    "**/api/model/**",
    "**/*Application*.class"
)
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        csv.required = false
        html.outputLocation = layout.buildDirectory.dir("jacocoHtml")
    }

    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(jacocoExcludedPatterns)
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)

    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }

    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(jacocoExcludedPatterns)
            }
        })
    )
}
