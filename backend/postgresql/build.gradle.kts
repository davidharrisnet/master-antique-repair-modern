plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.masterantique"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // JPA (Hibernate) + JDBC + HikariCP connection pool
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Password hashing for the first-login password change (no web security yet)
    implementation("org.springframework.security:spring-security-crypto")
    // PostgreSQL JDBC driver; the version comes from the Spring Boot BOM
    runtimeOnly("org.postgresql:postgresql")

    // Unit tests: JUnit 5, Mockito, AssertJ
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
