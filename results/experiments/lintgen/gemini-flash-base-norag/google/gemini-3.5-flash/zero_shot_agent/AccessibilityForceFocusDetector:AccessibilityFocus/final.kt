package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "performAction",
            "performAccessibilityAction",
            "sendAccessibilityEvent",
            "sendAccessibilityEventUnchecked"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val args = node.valueArguments
        if (args.isEmpty()) return
        val firstArg = args[0]

        val isFocusAction = isFocusAction(firstArg, context)
        if (!isFocusAction) return

        // Verify the class is relevant to avoid false positives on unrelated classes
        val containingClass = method.containingClass
        if (containingClass != null) {
            val qualifiedName = containingClass.qualifiedName
            if (qualifiedName != null) {
                val isRelevant = qualifiedName.startsWith("android.view.") ||
                        qualifiedName.startsWith("androidx.core.view.") ||
                        qualifiedName.contains("Accessibility") ||
                        qualifiedName.contains("View")
                if (!isRelevant) return
            }
        }

        report(context, node)
    }

    private fun isFocusAction(arg: UExpression, context: JavaContext): Boolean {
        val value = ConstantEvaluator.evaluate(context, arg)
        if (value is Number) {
            val intVal = value.toInt()
            if (intVal == 64 || intVal == 32768) {
                return true
            }
        }
        val src = arg.asSourceString()
        return src.contains("ACTION_ACCESSIBILITY_FOCUS") ||
                src.contains("TYPE_VIEW_ACCESSIBILITY_FOCUSED") ||
                src.contains("64") ||
                src.contains("32768") ||
                src.contains("0x40") ||
                src.contains("0x8000") ||
                src.contains("0X40") ||
                src.contains("0X8000")
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Forcing accessibility focus is discouraged as it interferes with screen readers and provides an inconsistent user experience."
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an \
                inconsistent user experience, especially across apps.
            """.trimIndent(),
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}