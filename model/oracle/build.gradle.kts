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
    // REST API (Spring MVC, embedded Tomcat, Jackson 3) and Bean Validation of the request bodies
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // OpenAPI document and Swagger UI; the 3.x line is the one for Spring Boot 4 (3.1.1 is built on Boot 4.1.0)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
    // Oracle JDBC driver (thin, Java 11+); the version comes from the Spring Boot BOM
    runtimeOnly("com.oracle.database.jdbc:ojdbc11")

    // Unit tests: JUnit 5, Mockito, AssertJ; MockMvc for the error-mapping test of the web layer
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// `./gradlew bootRun` with no MAR_DB_PASSWORD in the environment: make sure the database logins match the local
// password file (scripts/db-logins.sh creates it on first use), then hand mar_app's password to the application
// through its environment. The password is never printed and never on a command line.
val dbLoginsScript = file("scripts/db-logins.sh")
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    doFirst {
        if (System.getenv("MAR_DB_PASSWORD").isNullOrEmpty()) {
            val command = listOfNotNull(dbLoginsScript.path, System.getenv("MAR_DB_CONTAINER"))
            // The script prints only counts and error codes; show them on the console (not the Gradle daemon's).
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            process.inputStream.bufferedReader().forEachLine { println(it) }
            if (process.waitFor() != 0) {
                throw GradleException("scripts/db-logins.sh failed; see its message above")
            }
            val envFile = File(System.getenv("MAR_ENV_FILE") ?: "${System.getProperty("user.home")}/.config/mar/oracle.env")
            val password = envFile.readLines().firstOrNull { it.startsWith("MAR_DB_PASSWORD=") }
                ?.substringAfter("=")
            if (password.isNullOrEmpty()) throw GradleException("no MAR_DB_PASSWORD in $envFile")
            environment("MAR_DB_PASSWORD", password)
        }
    }
}
