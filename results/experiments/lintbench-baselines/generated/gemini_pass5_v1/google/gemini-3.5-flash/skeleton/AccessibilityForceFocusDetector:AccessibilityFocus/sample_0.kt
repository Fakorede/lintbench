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
import org.jetbrains.uast.USimpleNameReferenceExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            AccessibilityForceFocusDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.",
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("performAction")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubClassOf(method, "android.view.View", false) &&
            !evaluator.isMemberInSubClassOf(method, "android.view.accessibility.AccessibilityNodeInfo", false) &&
            !evaluator.isMemberInSubClassOf(method, "androidx.core.view.accessibility.AccessibilityNodeInfoCompat", false)
        ) {
            return
        }

        val args = node.valueArguments
        if (args.isNotEmpty()) {
            val firstArg = args[0]
            val evaluated = firstArg.evaluate()
            if (evaluated is Int) {
                // ACTION_ACCESSIBILITY_FOCUS = 64 (0x00000040)
                // ACTION_CLEAR_ACCESSIBILITY_FOCUS = 128 (0x00000080)
                if (evaluated == 64 || evaluated == 128) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience"
                    )
                }
            } else {
                val src = firstArg.asSourceString()
                if (src.contains("ACTION_ACCESSIBILITY_FOCUS") || src.contains("ACTION_CLEAR_ACCESSIBILITY_FOCUS")) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience"
                    )
                }
            }
        }
    }

    fun visitSimpleNameReferenceExpression(context: JavaContext, node: USimpleNameReferenceExpression) {
        // Left unimplemented as method calls to performAction are handled via visitMethodCall
    }
}