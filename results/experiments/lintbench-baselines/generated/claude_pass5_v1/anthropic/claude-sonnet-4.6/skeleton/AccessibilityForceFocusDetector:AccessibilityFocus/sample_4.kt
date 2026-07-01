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
        private const val VIEW_CLASS = "android.view.View"
        private const val ACCESSIBILITY_NODE_INFO_COMPAT_CLASS = "androidx.core.view.accessibility.AccessibilityNodeInfoCompat"
        private const val VIEW_COMPAT_CLASS = "androidx.core.view.ViewCompat"

        private const val PERFORM_ACTION_METHOD = "performAction"
        private const val SEND_ACCESSIBILITY_EVENT_METHOD = "sendAccessibilityEvent"
        private const val REQUEST_ACCESSIBILITY_FOCUS_METHOD = "requestAccessibilityFocus"
        private const val PERFORM_ACTION_FOR_VIRTUAL_VIEW_METHOD = "performActionForVirtualView"

        private const val ACTION_ACCESSIBILITY_FOCUS = "ACTION_ACCESSIBILITY_FOCUS"
        private const val ACCESSIBILITY_ACTION_ACCESSIBILITY_FOCUS = "ACCESSIBILITY_FOCUS"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        PERFORM_ACTION_METHOD,
        SEND_ACCESSIBILITY_EVENT_METHOD,
        REQUEST_ACCESSIBILITY_FOCUS_METHOD,
        PERFORM_ACTION_FOR_VIRTUAL_VIEW_METHOD,
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val containingClass = method.containingClass ?: return
        val evaluator = context.evaluator

        when (method.name) {
            PERFORM_ACTION_METHOD -> {
                if (evaluator.extendsClass(containingClass, ACCESSIBILITY_NODE_INFO_CLASS, false) ||
                    evaluator.extendsClass(containingClass, ACCESSIBILITY_NODE_INFO_COMPAT_CLASS, false) ||
                    evaluator.extendsClass(containingClass, VIEW_CLASS, false)
                ) {
                    val arguments = node.valueArguments
                    if (arguments.isNotEmpty()) {
                        val firstArg = arguments[0]
                        val text = firstArg.asSourceString()
                        if (text.contains(ACTION_ACCESSIBILITY_FOCUS) ||
                            text.contains(ACCESSIBILITY_ACTION_ACCESSIBILITY_FOCUS)
                        ) {
                            report(context, node)
                        }
                    }
                }
            }

            SEND_ACCESSIBILITY_EVENT_METHOD -> {
                if (evaluator.extendsClass(containingClass, VIEW_CLASS, false) ||
                    evaluator.extendsClass(containingClass, VIEW_COMPAT_CLASS, false)
                ) {
                    val arguments = node.valueArguments
                    if (arguments.isNotEmpty()) {
                        val firstArg = arguments[0]
                        val text = firstArg.asSourceString()
                        if (text.contains("TYPE_VIEW_ACCESSIBILITY_FOCUSED") ||
                            text.contains("TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED")
                        ) {
                            report(context, node)
                        }
                    }
                }
            }

            REQUEST_ACCESSIBILITY_FOCUS_METHOD -> {
                if (evaluator.extendsClass(containingClass, ACCESSIBILITY_NODE_INFO_CLASS, false) ||
                    evaluator.extendsClass(containingClass, ACCESSIBILITY_NODE_INFO_COMPAT_CLASS, false) ||
                    evaluator.extendsClass(containingClass, VIEW_CLASS, false)
                ) {
                    report(context, node)
                }
            }

            PERFORM_ACTION_FOR_VIRTUAL_VIEW_METHOD -> {
                val arguments = node.valueArguments
                if (arguments.size >= 2) {
                    val secondArg = arguments[1]
                    val text = secondArg.asSourceString()
                    if (text.contains(ACTION_ACCESSIBILITY_FOCUS) ||
                        text.contains(ACCESSIBILITY_ACTION_ACCESSIBILITY_FOCUS)
                    ) {
                        report(context, node)
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
        if (name == ACTION_ACCESSIBILITY_FOCUS) {
            val evaluator = context.evaluator
            val resolved = node.resolve()
            if (resolved != null) {
                val containingClass = (resolved as? com.intellij.psi.PsiMember)?.containingClass
                if (containingClass != null &&
                    (evaluator.extendsClass(containingClass, ACCESSIBILITY_NODE_INFO_CLASS, false) ||
                        evaluator.extendsClass(containingClass, ACCESSIBILITY_NODE_INFO_COMPAT_CLASS, false))
                ) {
                    // Only report if not already reported as part of a method call
                    val parent = node.uastParent
                    if (parent !is UCallExpression) {
                        report(context, node)
                    }
                }
            }
        }
    }

    override fun getApplicableReferenceNames(): List<String> = listOf(
        ACTION_ACCESSIBILITY_FOCUS,
    )

    private fun report(context: JavaContext, node: UElement) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Forcing accessibility focus interferes with screen readers and gives an " +
                "inconsistent user experience, especially across apps.",
        )
    }
}