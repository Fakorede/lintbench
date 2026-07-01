package com.android.tools.lint.checks

import com.android.tools.lint.client.api.GradleContext
import com.android.tools.lint.detector.api.*
import java.util.regex.Pattern

class AppBundleLocaleChangesDetector : Detector(), GradleScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.

                To disable locale splitting, add the following to your build.gradle:
                android {
                    bundle {
                        language {
                            enableSplit = false
                        }
                    }
                }
                Reference: https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.GRADLE_SCOPE
            )
        )

        private val ANDROID_BLOCK_PATTERN = Pattern.compile("""(?:^|\s)android\s*\{""", Pattern.MULTILINE)
        private val ENABLE_SPLIT_FALSE_PATTERN = Pattern.compile("""enableSplit\s*[=(]\s*false""")
    }

    override fun getApplicableFiles(): Scope = Scope.GRADLE_SCOPE

    override fun visitBuildScript(context: GradleContext) {
        val text = context.file.text

        if (!ANDROID_BLOCK_PATTERN.matcher(text).find()) {
            return
        }

        if (ENABLE_SPLIT_FALSE_PATTERN.matcher(text).find()) {
            return
        }

        context.report(
            ISSUE,
            Location.create(context.file),
            "This app may handle runtime locale changes, but the App Bundle is configured to split by locale by default. " +
                    "Add `bundle { language { enableSplit = false } }` to your build.gradle or use the Play Core library."
        )
    }
}