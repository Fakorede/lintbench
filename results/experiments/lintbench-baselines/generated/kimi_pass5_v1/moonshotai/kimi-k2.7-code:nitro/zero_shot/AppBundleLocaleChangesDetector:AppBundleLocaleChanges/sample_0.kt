package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.Location
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.getParentOfType

class AppBundleLocaleChangesDetector : Detector(), Detector.UElementHandler {

    override fun getApplicableUElementTypes(): List<Class<out UElement>>? =
        listOf(UCallExpression::class.java)

    override fun createUElementHandler(context: JavaContext): UElementHandler =
        object : UElementHandler {
            override fun visitCallExpression(node: UCallExpression) {
                val methodName = node.methodName ?: return
                val containingClass = node.resolve()?.containingClass?.qualifiedName ?: return

                val isLocaleChange = when (methodName) {
                    "setDefault" -> containingClass == "java.util.Locale"
                    "setLocale" -> containingClass == "android.content.res.Configuration"
                    "setApplicationLocales" -> containingClass == "androidx.appcompat.app.AppCompatDelegate"
                    "updateConfiguration" -> containingClass == "android.content.res.Resources"
                    else -> false
                }

                if (isLocaleChange) {
                    val message = "When changing locales at runtime, the Android App Bundle must be " +
                            "configured to not split by locale, or the Play Core library must be " +
                            "used to download additional locales at runtime."
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        message
                    )
                }
            }
        }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle locale split may prevent runtime locale changes",
            explanation = """
                When changing locales at runtime (for example to provide an in-app language switcher),
                the app may not have access to translated resources if the Android App Bundle is
                configured to split resources by locale.

                You should either disable locale splitting in the bundle configuration
                (e.g., `android.bundle.language.enableSplit = false`) or use the Play Core library
                to download the additional locales at runtime.

                See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes
                for more details.
            """,
            category = Category.I18N,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}