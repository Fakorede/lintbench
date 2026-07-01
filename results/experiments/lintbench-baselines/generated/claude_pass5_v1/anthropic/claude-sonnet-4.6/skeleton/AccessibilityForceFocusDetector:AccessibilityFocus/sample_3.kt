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

        private const val ACCESSIBILITY_NODE_INFO_CLASS = "android.view.accessibility.AccessibilityNodeInfo"
        private const val VIEW_COMPAT_CLASS = "androidx.core.view.ViewCompat"
        private const val VIEW_CLASS = "android.view.View"

        private val SEND_ACCESSIBILITY_EVENT_METHODS = listOf(
            "sendAccessibilityEvent",
            "sendAccessibilityEventUnchecked",
        )

        private val PERFORM_ACTION_METHOD = "performAccessibilityAction"

        private const val ACTION_ACCESSIBILITY_FOCUS = "ACTION_ACCESSIBILITY_FOCUS"
        private const val ACCESSIBILITY_ACTION_FOCUS = "ACCESSIBILITY_ACTION_FOCUS"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        "sendAccessibilityEvent",
        "sendAccessibilityEventUnchecked",
        "performAccessibilityAction",
        "performAction",
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val methodName = method.name
        val evaluator = context.evaluator

        if (methodName in SEND_ACCESSIBILITY_EVENT_METHODS) {
            // Check if the event type argument references TYPE_VIEW_ACCESSIBILITY_FOCUSED
            val args = node.valueArguments
            if (args.isNotEmpty()) {
                val firstArg = args[0]
                val argText = firstArg.asSourceString()
                if (argText.contains("TYPE_VIEW_ACCESSIBILITY_FOCUSED")) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Forcing accessibility focus by sending `TYPE_VIEW_ACCESSIBILITY_FOCUSED` " +
                            "interferes with screen readers and gives an inconsistent user experience.",
                    )
                    return
                }
            }
        }

        if (methodName == "performAccessibilityAction" || methodName == "performAction") {
            val args = node.valueArguments
            if (args.isNotEmpty()) {
                val firstArg = args[0]
                val argText = firstArg.asSourceString()
                if (argText.contains("ACTION_ACCESSIBILITY_FOCUS")) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Forcing accessibility focus by performing `ACTION_ACCESSIBILITY_FOCUS` " +
                            "interferes with screen readers and gives an inconsistent user experience.",
                    )
                    return
                }
            }

            // Check if method is on AccessibilityNodeInfo and first arg resolves to
            // AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
            if (evaluator.isMemberInClass(method, ACCESSIBILITY_NODE_INFO_CLASS)) {
                val args2 = node.valueArguments
                if (args2.isNotEmpty()) {
                    val argText = args2[0].asSourceString()
                    if (argText.contains("ACCESSIBILITY_FOCUS")) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Forcing accessibility focus interferes with screen readers and gives " +
                                "an inconsistent user experience.",
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
        if (name == ACTION_ACCESSIBILITY_FOCUS || name == ACCESSIBILITY_ACTION_FOCUS) {
            // Walk up to see if it's being used in a performAction/performAccessibilityAction call
            // to avoid double reporting; only report standalone field references that are
            // clearly being passed to focus-forcing APIs.
            // We report the method call in visitMethodCall, so here we only flag direct
            // references to the constant when they appear outside of a handled call.
            val parent = node.uastParent
            if (parent is UCallExpression) {
                // Will be handled by visitMethodCall
                return
            }
        }
    }
}