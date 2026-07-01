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
import org.jetbrains.uast.UElement
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
            explanation = "Forcing accessibility focus interferes with screen readers and gives an " +
                "inconsistent user experience, especially across apps.",
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val SEND_ACCESSIBILITY_EVENT = "sendAccessibilityEvent"
        private const val PERFORM_ACTION = "performAccessibilityAction"
        private const val REQUEST_ACCESSIBILITY_FOCUS = "ACTION_ACCESSIBILITY_FOCUS"
        private const val ACCESSIBILITY_EVENT_TYPE_VIEW_ACCESSIBILITY_FOCUSED =
            "TYPE_VIEW_ACCESSIBILITY_FOCUSED"

        private val APPLICABLE_METHOD_NAMES = listOf(
            SEND_ACCESSIBILITY_EVENT,
            PERFORM_ACTION,
        )
    }

    override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHOD_NAMES

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val methodName = method.name
        val arguments = node.valueArguments

        when (methodName) {
            SEND_ACCESSIBILITY_EVENT -> {
                if (arguments.isNotEmpty()) {
                    val firstArg = arguments[0]
                    val argText = firstArg.asSourceString()
                    if (argText.contains(ACCESSIBILITY_EVENT_TYPE_VIEW_ACCESSIBILITY_FOCUSED)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Forcing accessibility focus with `$SEND_ACCESSIBILITY_EVENT` " +
                                "and `TYPE_VIEW_ACCESSIBILITY_FOCUSED` interferes with screen " +
                                "readers and gives an inconsistent user experience.",
                        )
                    }
                }
            }
            PERFORM_ACTION -> {
                if (arguments.isNotEmpty()) {
                    val firstArg = arguments[0]
                    val argText = firstArg.asSourceString()
                    if (argText.contains(REQUEST_ACCESSIBILITY_FOCUS)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Forcing accessibility focus with `$PERFORM_ACTION` and " +
                                "`ACTION_ACCESSIBILITY_FOCUS` interferes with screen readers " +
                                "and gives an inconsistent user experience.",
                        )
                    }
                }
            }
        }
    }

    override fun visitSimpleNameReferenceExpression(
        context: JavaContext,
        node: USimpleNameReferenceExpression,
    ) {
        val name = node.identifier
        if (name == REQUEST_ACCESSIBILITY_FOCUS ||
            name == ACCESSIBILITY_EVENT_TYPE_VIEW_ACCESSIBILITY_FOCUSED
        ) {
            val parent = node.uastParent
            // Only report if this reference is not already inside a method call
            // that we handle in visitMethodCall, to avoid double-reporting.
            if (parent !is UCallExpression) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node as UElement),
                    "Using `$name` to force accessibility focus interferes with screen " +
                        "readers and gives an inconsistent user experience.",
                )
            }
        }
    }
}