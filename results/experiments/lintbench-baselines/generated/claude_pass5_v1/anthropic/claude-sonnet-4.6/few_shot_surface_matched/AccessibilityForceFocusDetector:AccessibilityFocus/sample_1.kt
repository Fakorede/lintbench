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
        private val EXPLANATION = """
            Forcing accessibility focus interferes with screen readers and gives an \
            inconsistent user experience, especially across apps.
        """.trimIndent()

        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = EXPLANATION,
            category = Category.A11Y,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true
        )

        private val APPLICABLE_METHOD_NAMES = listOf(
            "sendAccessibilityEvent",
            "performAccessibilityAction",
            "requestAccessibilityFocus"
        )

        private const val TYPE_VIEW_FOCUSED = "TYPE_VIEW_FOCUSED"
        private const val ACTION_ACCESSIBILITY_FOCUS = "ACTION_ACCESSIBILITY_FOCUS"
        private const val ACCESSIBILITY_EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"
        private const val ACCESSIBILITY_NODE_INFO_CLASS = "android.view.accessibility.AccessibilityNodeInfo"
        private const val VIEW_CLASS = "android.view.View"
        private const val ACCESSIBILITY_NODE_INFO_COMPAT_CLASS =
            "androidx.core.view.accessibility.AccessibilityNodeInfoCompat"
        private const val VIEW_COMPAT_CLASS = "androidx.core.view.ViewCompat"
    }

    override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHOD_NAMES

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        when (method.name) {
            "sendAccessibilityEvent" -> {
                // Check if the event type argument is TYPE_VIEW_FOCUSED
                val args = node.valueArguments
                if (args.size >= 1) {
                    val lastArg = args.last()
                    val argText = lastArg.asSourceString()
                    if (argText.contains("TYPE_VIEW_FOCUSED")) {
                        report(context, node, "Forcing accessibility focus by sending `TYPE_VIEW_FOCUSED` event interferes with screen readers.")
                        return
                    }
                }
                // Also check if the method belongs to View or a subclass
                if (evaluator.isMemberInSubClassOf(method, VIEW_CLASS)) {
                    val args2 = node.valueArguments
                    if (args2.size >= 1) {
                        val argText = args2.last().asSourceString()
                        if (argText.contains("TYPE_VIEW_FOCUSED")) {
                            report(context, node, "Forcing accessibility focus by sending `TYPE_VIEW_FOCUSED` event interferes with screen readers.")
                        }
                    }
                }
            }

            "performAccessibilityAction" -> {
                // Check if the action is ACTION_ACCESSIBILITY_FOCUS
                val args = node.valueArguments
                if (args.isNotEmpty()) {
                    val firstArg = args.first()
                    val argText = firstArg.asSourceString()
                    if (argText.contains("ACTION_ACCESSIBILITY_FOCUS")) {
                        report(context, node, "Forcing accessibility focus using `ACTION_ACCESSIBILITY_FOCUS` interferes with screen readers.")
                    }
                }
            }

            "requestAccessibilityFocus" -> {
                report(context, node, "Forcing accessibility focus using `requestAccessibilityFocus()` interferes with screen readers.")
            }
        }
    }

    override fun visitSimpleNameReferenceExpression(
        context: JavaContext,
        node: USimpleNameReferenceExpression
    ) {
        val name = node.identifier
        when (name) {
            TYPE_VIEW_FOCUSED -> {
                val resolved = node.resolve()
                if (resolved != null) {
                    val containingClass = (resolved as? com.intellij.psi.PsiField)
                        ?.containingClass?.qualifiedName
                    if (containingClass == ACCESSIBILITY_EVENT_CLASS) {
                        report(
                            context,
                            node,
                            "Using `AccessibilityEvent.TYPE_VIEW_FOCUSED` to force accessibility focus interferes with screen readers."
                        )
                    }
                }
            }

            ACTION_ACCESSIBILITY_FOCUS -> {
                val resolved = node.resolve()
                if (resolved != null) {
                    val containingClass = (resolved as? com.intellij.psi.PsiField)
                        ?.containingClass?.qualifiedName
                    if (containingClass == ACCESSIBILITY_NODE_INFO_CLASS ||
                        containingClass == ACCESSIBILITY_NODE_INFO_COMPAT_CLASS
                    ) {
                        report(
                            context,
                            node,
                            "Using `ACTION_ACCESSIBILITY_FOCUS` to force accessibility focus interferes with screen readers."
                        )
                    }
                }
            }
        }
    }

    private fun report(context: JavaContext, node: UCallExpression, message: String) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            message
        )
    }

    private fun report(context: JavaContext, node: USimpleNameReferenceExpression, message: String) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            message
        )
    }
}