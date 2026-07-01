package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import java.util.EnumSet

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner {

    override fun getApplicableFileTypes(): EnumSet<Scope> = EnumSet.of(Scope.GRADLE_FILE)

    override fun visitFile(context: Context) {
        val contents = context.getContents() ?: return
        val text = contents.toString()

        // Only check application modules, as App Bundles apply to them
        val isApplicationModule = text.contains("com.android.application")
        if (!isApplicationModule) return

        // Check if locale splitting is explicitly disabled
        // Supports Groovy DSL: enableSplit = false
        // Supports Kotlin DSL: enableSplit.set(false) or enableSplit = false
        val hasLocaleSplitDisabled = Regex("""enableSplit\s*(?:=\s*false|\.set\s*\(\s*false\s*\))""").containsMatchIn(text)

        if (!hasLocaleSplitDisabled) {
            context.report(
                ISSUE,
                Location.create(context.file),
                "When changing locales at runtime, the Android App Bundle must be configured to not split by locale (`android.bundle.language.enableSplit = false`) or the Play Core library must be used to download additional locales at runtime."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.

                By default, App Bundles strip out resources for languages that don't match the \
                device's current locale. If your app switches locales dynamically, those resources \
                will be missing unless you disable locale splits or fetch them via Play Core.

                To disable locale splits, add the following to your `build.gradle` file:
                ```groovy
                android {
                    bundle {
                        language {
                            enableSplit = false
                        }
                    }
                }
                ```
                If you are already handling this via the Play Core library, you can suppress this warning.

                Reference: https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                EnumSet.of(Scope.GRADLE_FILE)
            )
        )
    }
}