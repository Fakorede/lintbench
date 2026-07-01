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

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "sendAccessibilityEvent",
        "sendAccessibilityEventUnchecked",
        "performAccessibilityAction",
        "performAction"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val args = node.valueArguments
        if (args.isEmpty()) return

        val value = (args[0].evaluate() as? Number)?.toInt() ?: return
        if (value != TYPE_VIEW_FOCUSED && value != ACTION_FOCUS) return

        val methodName = method.name
        val receiverClass = method.containingClass?.qualifiedName ?: return

        when (methodName) {
            "sendAccessibilityEvent", "sendAccessibilityEventUnchecked" -> {
                if (!isViewLike(receiverClass) || value != TYPE_VIEW_FOCUSED) return
            }
            "performAccessibilityAction" -> {
                if (!isViewLike(receiverClass) && !isNodeInfoLike(receiverClass)) return
                if (value != ACTION_FOCUS) return
            }
            "performAction" -> {
                if (!isNodeInfoLike(receiverClass) || value != ACTION_FOCUS) return
            }
            else -> return
        }

        val message = when (methodName) {
            "sendAccessibilityEvent", "sendAccessibilityEventUnchecked" ->
                "Sending TYPE_VIEW_FOCUSED forces accessibility focus"
            else ->
                "Performing ACTION_FOCUS forces accessibility focus"
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            message
        )
    }

    private fun isViewLike(fqcn: String): Boolean {
        return fqcn.endsWith(".View") ||
                fqcn.endsWith(".ViewGroup") ||
                fqcn.endsWith(".ViewCompat")
    }

    private fun isNodeInfoLike(fqcn: String): Boolean {
        return fqcn.endsWith(".AccessibilityNodeInfo") ||
                fqcn.endsWith(".AccessibilityNodeInfoCompat")
    }

    companion object {
        private const val TYPE_VIEW_FOCUSED = 8
        private const val ACTION_FOCUS = 1

        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an \
                inconsistent user experience, especially across apps.
            """,
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