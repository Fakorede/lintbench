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

        private const val ACCESSIBILITY_NODE_INFO_COMPAT =
            "androidx.core.view.accessibility.AccessibilityNodeInfoCompat"
        private const val ACCESSIBILITY_NODE_INFO =
            "android.view.accessibility.AccessibilityNodeInfo"
        private const val VIEW_COMPAT =
            "androidx.core.view.ViewCompat"
        private const val VIEW_CLASS =
            "android.view.View"

        private const val PERFORM_ACTION = "performAction"
        private const val SEND_ACCESSIBILITY_EVENT = "sendAccessibilityEvent"
        private const val SEND_ACCESSIBILITY_EVENT_UNCHECKED = "sendAccessibilityEventUnchecked"
        private const val REQUEST_ACCESSIBILITY_FOCUS = "requestAccessibilityFocus"
        private const val PERFORM_ACCESSIBILITY_ACTION = "performAccessibilityAction"

        private const val ACTION_ACCESSIBILITY_FOCUS = "ACTION_ACCESSIBILITY_FOCUS"
        private const val TYPE_VIEW_ACCESSIBILITY_FOCUSED = "TYPE_VIEW_ACCESSIBILITY_FOCUSED"

        private val APPLICABLE_METHOD_NAMES = listOf(
            PERFORM_ACTION,
            SEND_ACCESSIBILITY_EVENT,
            SEND_ACCESSIBILITY_EVENT_UNCHECKED,
            REQUEST_ACCESSIBILITY_FOCUS,
            PERFORM_ACCESSIBILITY_ACTION,
        )
    }

    override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHOD_NAMES

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        when (method.name) {
            PERFORM_ACTION, PERFORM_ACCESSIBILITY_ACTION -> {
                // Check if called on AccessibilityNodeInfo or AccessibilityNodeInfoCompat
                val isNodeInfo = evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO) ||
                        evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO_COMPAT)
                val isViewCompat = evaluator.isMemberInClass(method, VIEW_COMPAT)
                val isView = evaluator.isMemberInSubClassOf(method, VIEW_CLASS)

                if (!isNodeInfo && !isViewCompat && !isView) return

                // Look for ACTION_ACCESSIBILITY_FOCUS in the arguments
                val args = node.valueArguments
                if (args.isEmpty()) return

                val firstArg = args[0]
                val argText = firstArg.asSourceString()
                if (argText.contains(ACTION_ACCESSIBILITY_FOCUS)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Forcing accessibility focus with `$ACTION_ACCESSIBILITY_FOCUS` interferes with screen readers"
                    )
                }
            }

            SEND_ACCESSIBILITY_EVENT, SEND_ACCESSIBILITY_EVENT_UNCHECKED -> {
                val isView = evaluator.isMemberInSubClassOf(method, VIEW_CLASS)
                val isViewCompat = evaluator.isMemberInClass(method, VIEW_COMPAT)
                if (!isView && !isViewCompat) return

                val args = node.valueArguments
                if (args.isEmpty()) return

                val argText = args.last().asSourceString()
                if (argText.contains(TYPE_VIEW_ACCESSIBILITY_FOCUSED)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Forcing accessibility focus by sending `$TYPE_VIEW_ACCESSIBILITY_FOCUSED` interferes with screen readers"
                    )
                }
            }

            REQUEST_ACCESSIBILITY_FOCUS -> {
                val isNodeInfo = evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO) ||
                        evaluator.isMemberInSubClassOf(method, ACCESSIBILITY_NODE_INFO_COMPAT)
                if (!isNodeInfo) return

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Forcing accessibility focus with `$REQUEST_ACCESSIBILITY_FOCUS` interferes with screen readers"
                )
            }
        }
    }

    override fun visitSimpleNameReferenceExpression(
        context: JavaContext,
        node: USimpleNameReferenceExpression
    ) {
        val name = node.identifier
        if (name != ACTION_ACCESSIBILITY_FOCUS && name != TYPE_VIEW_ACCESSIBILITY_FOCUSED) return

        val resolved = node.resolve() ?: return
        val containingClass = (resolved as? com.intellij.psi.PsiField)?.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName != ACCESSIBILITY_NODE_INFO &&
            qualifiedName != ACCESSIBILITY_NODE_INFO_COMPAT &&
            qualifiedName != "android.view.accessibility.AccessibilityEvent" &&
            qualifiedName != "androidx.core.view.accessibility.AccessibilityEventCompat"
        ) return

        // Avoid double-reporting: only report if this reference is NOT inside a method call
        // that we already handle in visitMethodCall. We check the parent chain.
        val parent = node.uastParent
        // If it's a direct argument to a call we handle, skip it (already reported above).
        // Otherwise report standalone field access that forces focus.
        val callParent = generateSequence(parent) { it.uastParent }
            .filterIsInstance<UCallExpression>()
            .firstOrNull()

        if (callParent != null) {
            val callName = callParent.methodName ?: return
            if (callName in APPLICABLE_METHOD_NAMES) return
        }

        val message = when (name) {
            ACTION_ACCESSIBILITY_FOCUS ->
                "Using `$ACTION_ACCESSIBILITY_FOCUS` to force accessibility focus interferes with screen readers"
            TYPE_VIEW_ACCESSIBILITY_FOCUSED ->
                "Using `$TYPE_VIEW_ACCESSIBILITY_FOCUSED` to force accessibility focus interferes with screen readers"
            else -> return
        }

        context.report(ISSUE, node, context.getLocation(node), message)
    }
}