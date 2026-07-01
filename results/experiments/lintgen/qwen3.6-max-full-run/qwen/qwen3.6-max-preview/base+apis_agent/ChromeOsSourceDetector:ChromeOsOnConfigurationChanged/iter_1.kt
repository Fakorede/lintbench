package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.getContainingUMethod
import java.util.EnumSet

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val REDRAW_METHODS = listOf(
            "requestLayout",
            "invalidate",
            "postInvalidate",
            "forceLayout",
            "setLayoutParams"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Poor performance with APIs inside onConfigurationChanged()",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an \
                onConfigurationChanged() API call occurs. If your onConfigurationChanged() \
                method contains any code that can cause a redraw, your app might take a performance \
                hit on large screens. To fix the issue, ensure your onConfigurationChanged() method \
                does not contain any calls to UI redraw logic for specific elements.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE)
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> = REDRAW_METHODS

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingMethod = node.getContainingUMethod() ?: return
        if (containingMethod.name != "onConfigurationChanged") return

        val parameters = containingMethod.uastParameters
        if (parameters.size != 1) return

        val paramType = parameters[0].type ?: return
        if (!context.evaluator.typeMatches(paramType, "android.content.res.Configuration")) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Avoid calling `${node.methodName}` inside `onConfigurationChanged` as it may cause poor performance on large screens."
        )
    }
}