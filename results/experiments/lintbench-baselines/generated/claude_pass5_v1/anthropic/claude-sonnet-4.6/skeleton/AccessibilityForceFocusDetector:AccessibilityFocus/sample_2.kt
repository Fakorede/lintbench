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
        private const val REQUEST_ACCESSIBILITY_FOCUS = "ACTION_ACCESSIBILITY_FOCUS"
        private const val ACCESSIBILITY_NODE_INFO_CLASS = "android.view.accessibility.AccessibilityNodeInfo"
        private const val VIEW_CLASS = "android.view.View"
        private const val ACCESSIBILITY_EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"
        private const val TYPE_VIEW_ACCESSIBILITY_FOCUSED = "TYPE_VIEW_ACCESSIBILITY_FOCUSED"

        private val APPLICABLE_METHODS = listOf(
            SEND_ACCESSIBILITY_EVENT,
            PERFORM_ACTION,
        )
    }

    override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHODS

    override fun getApplicableReferenceNames(): List<String> = listOf(
        REQUEST_ACCESSIBILITY_FOCUS,
        TYPE_VIEW_ACCESSIBILITY_FOCUSED,
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val methodName = method.name
        val evaluator = context.evaluator

        when (methodName) {
            SEND_ACCESSIBILITY_EVENT -> {
                if (evaluator.isMemberInSubClassOf(method, VIEW_CLASS)) {
                    val arguments = node.valueArguments
                    if (arguments.isNotEmpty()) {
                        val firstArg = arguments[0]
                        val value = context.evaluator.getConstantValue(firstArg)
                        if (value is Int && value == 32768 /* TYPE_VIEW_ACCESSIBILITY_FOCUSED */) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Forcing accessibility focus with `sendAccessibilityEvent` and " +
                                    "`TYPE_VIEW_ACCESSIBILITY_FOCUSED` interferes with screen readers",
                            )
                        }
                    }
                }
            }
            PERFORM_ACTION -> {
                if (evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO_CLASS)) {
                    val arguments = node.valueArguments
                    if (arguments.isNotEmpty()) {
                        val firstArg = arguments[0]
                        val value = context.evaluator.getConstantValue(firstArg)
                        if (value is Int && value == 64 /* ACTION_ACCESSIBILITY_FOCUS */) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Forcing accessibility focus with `performAction` and " +
                                    "`ACTION_ACCESSIBILITY_FOCUS` interferes with screen readers",
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
        val resolved = node.resolve()

        when (name) {
            REQUEST_ACCESSIBILITY_FOCUS -> {
                val evaluator = context.evaluator
                if (resolved != null && evaluator.isMemberInClass(resolved, ACCESSIBILITY_NODE_INFO_CLASS)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node as UElement),
                        "Using `ACTION_ACCESSIBILITY_FOCUS` forces accessibility focus and " +
                            "interferes with screen readers",
                    )
                }
            }
            TYPE_VIEW_ACCESSIBILITY_FOCUSED -> {
                val evaluator = context.evaluator
                if (resolved != null && evaluator.isMemberInClass(resolved, ACCESSIBILITY_EVENT_CLASS)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node as UElement),
                        "Using `TYPE_VIEW_ACCESSIBILITY_FOCUSED` forces accessibility focus and " +
                            "interferes with screen readers",
                    )
                }
            }
        }
    }
}