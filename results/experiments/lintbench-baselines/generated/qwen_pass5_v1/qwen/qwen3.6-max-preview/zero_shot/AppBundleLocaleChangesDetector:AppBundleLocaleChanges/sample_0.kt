package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import java.util.EnumSet

@Suppress("UnstableApiUsage")
class AppBundleLocaleChangesDetector : Detector(), Detector.UastScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.

                To disable locale splits, add the following to your `build.gradle`:
                ```groovy
                android {
                    bundle {
                        language {
                            enableSplit = false
                        }
                    }
                }
                ```
                Alternatively, use the Play Core library to dynamically download locale resources.

                Reference: https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE_SCOPE)
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        "setLocale",
        "setLocales",
        "updateConfiguration",
        "setApplicationLocales"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val methodName = node.methodName ?: return

        val isRelevantCall = when (methodName) {
            "setLocale", "setLocales" -> evaluator.isMemberInClass(method, "android.content.res.Configuration")
            "updateConfiguration" -> evaluator.isMemberInClass(method, "android.content.res.Resources")
            "setApplicationLocales" -> evaluator.isMemberInClass(method, "androidx.appcompat.app.AppCompatDelegate")
            else -> false
        }

        if (isRelevantCall) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Runtime locale changes require App Bundle configuration. " +
                        "Disable locale splits in build.gradle or use Play Core to download locales dynamically."
            )
        }
    }
}