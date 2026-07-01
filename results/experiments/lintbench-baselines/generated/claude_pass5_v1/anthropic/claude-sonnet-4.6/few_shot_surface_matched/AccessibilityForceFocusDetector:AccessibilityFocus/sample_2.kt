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
        private const val VIEW_CLASS = "android.view.View"
        private const val ACCESSIBILITY_NODE_INFO_CLASS = "android.view.accessibility.AccessibilityNodeInfo"
        private const val VIEW_COMPAT_CLASS = "androidx.core.view.ViewCompat"

        private const val MESSAGE =
            "Forcing accessibility focus interferes with screen readers and gives an " +
                "inconsistent user experience, especially across apps."

        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an \
                inconsistent user experience, especially across apps.
            """,
            category = Category.A11Y,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true,
        )

        private val APPLICABLE_METHOD_NAMES = listOf(
            "sendAccessibilityEvent",
            "performAccessibilityAction",
            "requestAccessibilityFocus",
            "setAccessibilityFocused",
            "performAction"
        )

        private val FORCE_FOCUS_FIELD_NAMES = setOf(
            "ACTION_ACCESSIBILITY_FOCUS",
            "TYPE_VIEW_ACCESSIBILITY_FOCUSED",
            "ACTION_TYPE_ACCESSIBILITY_FOCUS"
        )
    }

    override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHOD_NAMES

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        when (method.name) {
            "sendAccessibilityEvent" -> {
                if (!evaluator.isMemberInSubClassOf(method, VIEW_CLASS) &&
                    !evaluator.isMemberInClass(method, VIEW_COMPAT_CLASS)
                ) {
                    return
                }
                val args = node.valueArguments
                for (arg in args) {
                    val text = arg.asSourceString()
                    if (text.contains("TYPE_VIEW_ACCESSIBILITY_FOCUSED") ||
                        text.contains("128")
                    ) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            MESSAGE
                        )
                        return
                    }
                }
            }

            "performAccessibilityAction", "performAction" -> {
                val args = node.valueArguments
                for (arg in args) {
                    val text = arg.asSourceString()
                    if (text.contains("ACTION_ACCESSIBILITY_FOCUS") ||
                        text.contains("64")
                    ) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            MESSAGE
                        )
                        return
                    }
                }
            }

            "requestAccessibilityFocus" -> {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    MESSAGE
                )
            }

            "setAccessibilityFocused" -> {
                val arg = node.valueArguments.firstOrNull() ?: return
                val text = arg.asSourceString()
                if (text == "true") {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE
                    )
                }
            }
        }
    }

    override fun visitSimpleNameReferenceExpression(
        context: JavaContext,
        node: USimpleNameReferenceExpression
    ) {
        val name = node.identifier
        if (name !in FORCE_FOCUS_FIELD_NAMES) return

        val resolved = node.resolve() ?: return
        val containingClass = (resolved as? com.intellij.psi.PsiField)
            ?.containingClass?.qualifiedName ?: return

        if (containingClass == ACCESSIBILITY_NODE_INFO_CLASS ||
            containingClass == VIEW_CLASS ||
            containingClass == "android.view.accessibility.AccessibilityEvent"
        ) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                MESSAGE
            )
        }
    }
}