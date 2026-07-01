package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Detector.GradleScanner
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import java.util.EnumSet

class AppBundleLocaleChangesDetector : Detector(), GradleScanner {

    override fun getApplicableFiles(): EnumSet<Scope> = Scope.GRADLE_SCOPE

    override fun visitBuildScript(context: GradleContext, cookie: Any) {
        val contents = context.getContents()?.toString() ?: return
        if (!isApplicationModule(contents)) return

        val hasPlayCore = PLAY_CORE_REGEX.containsMatchIn(contents)
        val languageSplitDisabled = isLanguageSplitDisabled(contents)

        if (!hasPlayCore && !languageSplitDisabled) {
            val message = "When changing locales at runtime, the App Bundle must not split by " +
                "locale or the Play Core library must be used to download additional locales at " +
                "runtime. Either set bundle.language.enableSplit = false or add a Play Core " +
                "dependency. See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes"
            context.report(ISSUE, computeReportLocation(context, contents), message)
        }
    }

    private fun isApplicationModule(contents: String): Boolean {
        return APPLICATION_PLUGIN_REGEX.containsMatchIn(contents)
    }

    private fun isLanguageSplitDisabled(contents: String): Boolean {
        val languageBlocks = extractBlocks(contents, "language")
        return languageBlocks.any { ENABLE_SPLIT_FALSE_REGEX.containsMatchIn(it) }
    }

    private fun extractBlocks(contents: String, keyword: String): List<String> {
        val blocks = mutableListOf<String>()
        val regex = Regex("""$keyword\s*\{""")
        var match = regex.find(contents)
        while (match != null) {
            val blockStart = match.range.last + 1
            val blockEnd = findMatchingBrace(contents, blockStart)
            if (blockEnd != -1) {
                blocks.add(contents.substring(blockStart, blockEnd))
            }
            match = match.next()
        }
        return blocks
    }

    private fun findMatchingBrace(contents: String, start: Int): Int {
        var depth = 1
        var i = start
        while (i < contents.length && depth > 0) {
            when (contents[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            i++
        }
        return if (depth == 0) i - 1 else -1
    }

    private fun computeReportLocation(context: GradleContext, contents: String): Location {
        val token = Regex("""\bbundle\b""").find(contents)
            ?: Regex("""\bandroid\b""").find(contents)
        return if (token != null) {
            Location.create(
                context.file,
                contents,
                token.range.first,
                token.range.last + 1
            )
        } else {
            Location.create(context.file)
        }
    }

    companion object {
        private val APPLICATION_PLUGIN_REGEX = Regex(
            """(?:apply\s+plugin\s*[:=]\s*['"]|id\s*\(?\s*['"]|id\s+['"]?)com\.android\.application\b"""
        )

        private val ENABLE_SPLIT_FALSE_REGEX = Regex(
            """enableSplit\s*(?:=\s*)?false"""
        )

        private val PLAY_CORE_REGEX = Regex(
            """com\.google\.android\.play:(?:core(?:-ktx)?|feature-delivery(?:-ktx)?)\b"""
        )

        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (for example, to provide an in-app language
                switcher), the Android App Bundle must be configured to not split by locale or
                the Play Core library must be used to download additional locales at runtime.
                Otherwise users may see missing resources after a locale change.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.GRADLE_SCOPE
            )
        )
    }
}