import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

plugins {
	java
	id("org.springframework.boot") version "3.2.0-SNAPSHOT"  apply false
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

apply(plugin = "io.spring.dependency-management")

the<DependencyManagementExtension>().apply {
	imports {
		mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
	}
}

java {
	sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
	mavenCentral()
	maven { url = uri("https://repo.spring.io/milestone") }
	maven { url = uri("https://repo.spring.io/snapshot") }
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-webflux")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
