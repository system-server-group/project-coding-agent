plugins {
	java
    checkstyle
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.5.1"
}

group = "com.system-server"
version = "0.0.1-SNAPSHOT"

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-parameters"))
    options.encoding = "UTF-8"
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

checkstyle {
    toolVersion = "10.20.2"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxWarnings = 0
}

tasks.withType<Checkstyle>().configureEach {
    // testgen-exec のハーネスキットは「無改変で転写」が規範のため、本プロジェクトの
    // コーディング規約 (checkstyle/spotless) の対象外とする。
    exclude("testgen/e2e/support/**")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

spotless {
    java {
        target("src/**/*.java")
        // 転写したハーネスキットはキットが正本のため整形対象から除く (上記 checkstyle と同旨)。
        targetExclude("src/test/java/testgen/e2e/support/**")
        eclipse("4.36").configFile("config/eclipse/eclipse-formatter.xml")
        // Google Checks の CustomImportOrder (STATIC###THIRD_PARTY_PACKAGE) と整合させる:
        //   - "\\#" prefix が static import を意味する
        //   - "" は static 以外の全 import を 1 グループにアルファベット順で並べる
        importOrder("\\#", "")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("misc") {
        target(
            "*.gradle.kts",
            ".gitignore",
            "src/**/*.yml",
            "src/**/*.yaml",
            "src/**/*.xml",
            "src/**/*.properties",
            "src/**/*.html",
            "src/**/*.css",
            "src/**/*.js"
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}

repositories {
	mavenCentral()
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

dependencies {
    // Deps
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    testImplementation("org.springframework.boot:spring-boot-starter-mail-test")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-jdbc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-session-jdbc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.mybatis.spring.boot:mybatis-spring-boot-starter-test:4.0.1")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
	compileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
	testImplementation("org.testcontainers:testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.1")
	runtimeOnly("org.postgresql:postgresql")

    // WebJar
    implementation("org.webjars:webjars-locator-lite:1.0.1")
    implementation("org.webjars:bootstrap:5.3.3")
    implementation("org.webjars.npm:bootstrap-icons:1.11.3")
    implementation("org.webjars.npm:ag-grid-community:32.2.0")
}

// E2E テストハーネス (testgen-exec キット) の Gradle 設定。
// 専用依存・e2eTest / e2eEvidence / e2eEvidenceIndex タスクは当該スクリプトが持つ。
// 注意: この適用より後で test タスクの useJUnitPlatform を再設定しないこと (e2e タグ除外が失われる)。
apply(from = "testgen.e2e.gradle.kts")
