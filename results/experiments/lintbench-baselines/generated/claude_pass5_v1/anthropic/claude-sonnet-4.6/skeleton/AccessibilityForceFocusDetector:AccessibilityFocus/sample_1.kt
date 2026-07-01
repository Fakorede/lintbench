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
        private const val PERFORM_ACTION = "performAction"
        private const val REQUEST_FOCUS = "requestFocus"
        private const val ACCESSIBILITY_ACTION_ACCESSIBILITY_FOCUS = "ACTION_ACCESSIBILITY_FOCUS"
        private const val TYPE_VIEW_ACCESSIBILITY_FOCUSED = "TYPE_VIEW_ACCESSIBILITY_FOCUSED"

        private val APPLICABLE_METHOD_NAMES = listOf(
            SEND_ACCESSIBILITY_EVENT,
            PERFORM_ACTION,
            REQUEST_FOCUS,
        )

        private val SUSPICIOUS_FIELD_NAMES = setOf(
            ACCESSIBILITY_ACTION_ACCESSIBILITY_FOCUS,
            TYPE_VIEW_ACCESSIBILITY_FOCUSED,
        )
    }

    override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHOD_NAMES

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val methodName = method.name
        val evaluator = context.evaluator

        when (methodName) {
            SEND_ACCESSIBILITY_EVENT -> {
                // Check if the event type argument is TYPE_VIEW_ACCESSIBILITY_FOCUSED
                val args = node.valueArguments
                if (args.isNotEmpty()) {
                    val firstArg = args[0]
                    val argText = firstArg.asSourceString()
                    if (argText.contains(TYPE_VIEW_ACCESSIBILITY_FOCUSED)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Forcing accessibility focus with `$TYPE_VIEW_ACCESSIBILITY_FOCUSED` " +
                                "interferes with screen readers and gives an inconsistent user experience",
                        )
                        return
                    }
                }
            }

            PERFORM_ACTION -> {
                // Check if the action argument is ACTION_ACCESSIBILITY_FOCUS
                val args = node.valueArguments
                if (args.isNotEmpty()) {
                    val firstArg = args[0]
                    val argText = firstArg.asSourceString()
                    if (argText.contains(ACCESSIBILITY_ACTION_ACCESSIBILITY_FOCUS)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Forcing accessibility focus with `$ACCESSIBILITY_ACTION_ACCESSIBILITY_FOCUS` " +
                                "interferes with screen readers and gives an inconsistent user experience",
                        )
                        return
                    }
                }
            }

            REQUEST_FOCUS -> {
                val containingClass = method.containingClass
                if (containingClass != null &&
                    (evaluator.inheritsFrom(containingClass, "android.view.View", false) ||
                        evaluator.inheritsFrom(containingClass, "android.view.accessibility.AccessibilityNodeInfo", false))
                ) {
                    // Check if this is related to accessibility focus
                    val args = node.valueArguments
                    if (args.isNotEmpty()) {
                        val firstArg = args[0]
                        val argText = firstArg.asSourceString()
                        if (argText.contains("ACCESSIBILITY_FOCUS") ||
                            argText.contains("FOCUS_ACCESSIBILITY")
                        ) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Forcing accessibility focus interferes with screen readers and " +
                                    "gives an inconsistent user experience",
                            )
                        }
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
        if (name in SUSPICIOUS_FIELD_NAMES) {
            val parent = node.uastParent
            // Only report if the reference is used as a standalone expression or in a context
            // that suggests forcing focus (not just importing or declaring)
            if (parent is UCallExpression) {
                // Already handled in visitMethodCall
                return
            }
            context.report(
                ISSUE,
                node,
                context.getLocation(node as UElement),
                "Using `$name` to force accessibility focus interferes with screen readers " +
                    "and gives an inconsistent user experience",
            )
        }
    }

    override fun getApplicableReferenceNames(): List<String> {
        return SUSPICIOUS_FIELD_NAMES.toList()
    }
}