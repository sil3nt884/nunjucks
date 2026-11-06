import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.njk"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Build against a locally installed IDE when `localIdePath` is set (fast, no download),
        // otherwise download the IDE named by `platformVersion`.
        val localIde = providers.gradleProperty("localIdePath").orNull
        if (localIde != null && file(localIde).exists()) {
            local(localIde)
        } else {
            intellijIdea(providers.gradleProperty("platformVersion").get())
        }
        // Optional at runtime (see njk-javascript.xml); needed to compile the TypeScript integration.
        bundledPlugin("JavaScript")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

intellijPlatform {
    pluginConfiguration {
        id = "dev.njk.nunjucks"
        name = "Nunjucks Support"
        version = project.version.toString()
        // Kept up to date by .github/workflows/platform-update.yml after each new IDE release passes the checks.
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }
    pluginVerification {
        ides {
            // The oldest and newest IDEs the plugin claims to support, from gradle.properties.
            providers.gradleProperty("verifyIdeVersions").get().split(',').map(String::trim).filter(String::isNotEmpty)
                .forEach { create(IntelliJPlatformType.IntellijIdea, it) }
        }
        // Fail on real breaks only. Using the internal ActionRemoteBehaviorSpecification (needed for remote
        // development) is reported but allowed; if it ever goes away, that shows up as a compatibility problem.
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
            VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
        )
    }
    buildSearchableOptions = false
}

// The downloaded IntelliJ IDEA distribution includes the Ultimate licensing module, which can't start in the
// test environment and fails every test. The plugin doesn't need it.
tasks.prepareTestSandbox {
    disabledPlugins.add("com.intellij.modules.ultimate")
}

// Open the sample project when trying the plugin with ./gradlew runIde
tasks.runIde {
    args(layout.projectDirectory.dir("sample").asFile.absolutePath)
}
