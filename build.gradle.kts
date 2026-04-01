import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
}

subprojects {
    tasks.withType<Test>().configureEach {
        // Avoid leaking machine-level Windows Java environment quirks into forked test JVMs.
        environment("CLASSPATH", "")
    }

    // Gradle 8.13 on Windows can fail if Kotlin tasks leave declared cache directories absent.
    tasks.matching { task -> task.name.endsWith("Kotlin") }.configureEach {
        val kotlinLookupDir = project.layout.buildDirectory.dir("kotlin/$name/cacheable/caches-jvm/lookups")
        doFirst { kotlinLookupDir.get().asFile.mkdirs() }
        doLast { kotlinLookupDir.get().asFile.mkdirs() }
    }

    // Some lint analyze tasks can declare a partial result file that is never materialized on Windows.
    tasks.matching { task -> task.name.startsWith("lintAnalyze") }.configureEach {
        val isAndroidTestTask = name.endsWith("AndroidTest")
        val variantName = if (isAndroidTestTask) {
            name.removePrefix("lintAnalyze").removeSuffix("AndroidTest")
        } else {
            name.removePrefix("lintAnalyze")
        }.replaceFirstChar(Char::lowercaseChar)
        val partialResultsRoot = if (isAndroidTestTask) {
            "intermediates/android_test_lint_partial_results"
        } else {
            "intermediates/lint_partial_results"
        }
        val partialResultFile = project.layout.buildDirectory.file(
            "$partialResultsRoot/$variantName/$name/out/lint-partial.xml",
        )
        val ensurePartialResult = {
            val file = partialResultFile.get().asFile
            file.parentFile.mkdirs()
            if (!file.exists()) {
                file.writeText("""<?xml version="1.0" encoding="UTF-8"?><issues format="6" by="lint" client="gradle" />""")
            }
        }
        doFirst { ensurePartialResult() }
        doLast { ensurePartialResult() }
    }
}
