package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import java.util.EnumSet

class AppBundleLocaleChangesDetector : Detector() {
    companion object {
        @JvmField
        val ISSUE = Issue.create(
            "AppBundleLocaleChanges",
            "App Bundle handling of runtime locale changes",
            "When changing locales at runtime (e.g. to provide an in-app language switcher), " +
                "the Android App Bundle must be configured to not split by locale or the Play Core " +
                "library must be used to download additional locales at runtime.\n" +
                "Reference documentation:\n" +
                "  - https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(AppBundleLocaleChangesDetector::class.java, EnumSet.of(Scope.GRADLE_FILE))
        )
    }

    override fun getApplicableFiles(): EnumSet<Scope> = EnumSet.of(Scope.GRADLE_FILE)

    override fun run(context: Context) {
        val contents = context.getContents()?.toString() ?: return
        if (!contents.contains("com.android.application")) return

        val hasLanguageSplitDisabled = contents.contains("enableSplit") &&
            contents.contains("false") &&
            contents.contains("language")

        if (!hasLanguageSplitDisabled) {
            context.report(
                ISSUE,
                Location.create(context.file),
                "Configure `bundle { language { enableSplit = false } }` to support runtime locale changes, " +
                    "or use the Play Core library to download additional locales at runtime."
            )
        }
    }
}