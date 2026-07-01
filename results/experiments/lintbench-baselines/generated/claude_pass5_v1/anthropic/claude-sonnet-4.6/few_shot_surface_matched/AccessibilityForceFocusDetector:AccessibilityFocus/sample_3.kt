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
        )

        private val ACCESSIBILITY_FOCUS_FIELD_NAMES = setOf(
            "ACTION_ACCESSIBILITY_FOCUS",
            "TYPE_VIEW_ACCESSIBILITY_FOCUSED",
            "TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED",
            "ACCESSIBILITY_FOCUS",
        )

        private const val ACCESSIBILITY_EVENT_CLASS = "android.view.accessibility.AccessibilityEvent"
        private const val ACCESSIBILITY_NODE_INFO_CLASS = "android.view.accessibility.AccessibilityNodeInfo"
        private const val VIEW_CLASS = "android.view.View"
        private const val VIEW_COMPAT_CLASS = "androidx.core.view.ViewCompat"
        private const val ACCESSIBILITY_NODE_INFO_COMPAT_CLASS =
            "androidx.core.view.accessibility.AccessibilityNodeInfoCompat"
    }

    override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHOD_NAMES

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        when (method.name) {
            "sendAccessibilityEvent" -> {
                if (!evaluator.isMemberInSubClassOf(method, VIEW_CLASS) &&
                    !evaluator.isMemberInSubClassOf(method, "android.view.ViewGroup")
                ) {
                    return
                }
                val eventTypeArg = node.valueArguments.firstOrNull() ?: return
                val constantValue = evaluator.constantEvaluator.evaluate(eventTypeArg)
                // TYPE_VIEW_ACCESSIBILITY_FOCUSED = 0x00008000 = 32768
                // TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED = 0x00010000 = 65536
                if (constantValue == 32768 || constantValue == 65536) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE
                    )
                }
            }

            "performAccessibilityAction" -> {
                val inNodeInfo = evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO_CLASS) ||
                    evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO_COMPAT_CLASS)
                val inView = evaluator.isMemberInSubClassOf(method, VIEW_CLASS)
                if (!inNodeInfo && !inView) return

                val actionArg = node.valueArguments.firstOrNull() ?: return
                val constantValue = evaluator.constantEvaluator.evaluate(actionArg)
                // ACTION_ACCESSIBILITY_FOCUS = 0x00000040 = 64
                // ACTION_CLEAR_ACCESSIBILITY_FOCUS = 0x00000080 = 128
                if (constantValue == 64 || constantValue == 128) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE
                    )
                }
            }

            "requestAccessibilityFocus" -> {
                if (evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO_CLASS) ||
                    evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO_COMPAT_CLASS) ||
                    evaluator.isMemberInSubClassOf(method, VIEW_CLASS) ||
                    evaluator.isMemberInSubClassOf(method, VIEW_COMPAT_CLASS)
                ) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE
                    )
                }
            }

            "setAccessibilityFocused" -> {
                if (evaluator.isMemberInSubClassOf(method, VIEW_CLASS) ||
                    evaluator.isMemberInSubClassOf(method, VIEW_COMPAT_CLASS)
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
    }

    override fun visitSimpleNameReferenceExpression(
        context: JavaContext,
        node: USimpleNameReferenceExpression
    ) {
        val name = node.identifier
        if (name !in ACCESSIBILITY_FOCUS_FIELD_NAMES) return

        val resolved = node.resolve() ?: return
        val containingClass = (resolved as? com.intellij.psi.PsiField)?.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName == ACCESSIBILITY_EVENT_CLASS ||
            qualifiedName == ACCESSIBILITY_NODE_INFO_CLASS ||
            qualifiedName == ACCESSIBILITY_NODE_INFO_COMPAT_CLASS ||
            qualifiedName == VIEW_CLASS ||
            qualifiedName == VIEW_COMPAT_CLASS
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