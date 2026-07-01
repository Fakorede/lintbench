package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.getUastParentOfType

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ACCESSIBILITY_FOCUS: Issue = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an
                inconsistent user experience, especially across apps. Avoid explicitly
                requesting focus or sending focus accessibility events.
            """,
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(AccessibilityForceFocusDetector::class.java, Scope.JAVA_FILE_SCOPE),
            androidSpecific = true,
        )

        private const val ACCESSIBILITY_NODE_INFO = "android.view.accessibility.AccessibilityNodeInfo"
        private const val ACCESSIBILITY_NODE_ACTION = "android.view.accessibility.AccessibilityNodeInfo\$AccessibilityAction"
        private const val ACCESSIBILITY_EVENT = "android.view.accessibility.AccessibilityEvent"
        private const val VIEW = "android.view.View"
    }

    override fun getApplicableMethodNames(): List<String> =
        listOf("performAction", "performAccessibilityAction", "sendAccessibilityEvent")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        when (method.name) {
            "performAction" -> {
                if (!context.evaluator.isMemberInClass(method, ACCESSIBILITY_NODE_INFO)) return
                if (isForcingFocusAction(node.valueArguments.firstOrNull())) {
                    reportIncident(context, node)
                }
            }
            "performAccessibilityAction" -> {
                if (!context.evaluator.isMemberInClass(method, VIEW)) return
                if (isForcingFocusAction(node.valueArguments.firstOrNull())) {
                    reportIncident(context, node)
                }
            }
            "sendAccessibilityEvent" -> {
                if (!context.evaluator.isMemberInClass(method, VIEW)) return
                if (isFocusEventType(node.valueArguments.firstOrNull())) {
                    reportIncident(context, node)
                }
            }
        }
    }

    override fun visitSimpleNameReferenceExpression(
        context: JavaContext,
        node: USimpleNameReferenceExpression
    ) {
        val parentCall = node.getUastParentOfType(UCallExpression::class.java)
        if (parentCall != null && isKnownFocusMethod(context, parentCall)) {
            return
        }

        val field = node.resolve() as? PsiField ?: return
        val className = field.containingClass?.qualifiedName ?: return
        val name = field.name

        if ((name == "ACTION_FOCUS" &&
                (className == ACCESSIBILITY_NODE_INFO || className == ACCESSIBILITY_NODE_ACTION))
            || (name == "TYPE_VIEW_FOCUSED" && className == ACCESSIBILITY_EVENT)
        ) {
            reportIncident(context, node)
        }
    }

    private fun isKnownFocusMethod(context: JavaContext, call: UCallExpression): Boolean {
        val method = call.resolve() ?: return false
        return when (method.name) {
            "performAction" -> context.evaluator.isMemberInClass(method, ACCESSIBILITY_NODE_INFO)
            "performAccessibilityAction",
            "sendAccessibilityEvent" -> context.evaluator.isMemberInClass(method, VIEW)
            else -> false
        }
    }

    private fun isForcingFocusAction(argument: UExpression?): Boolean {
        val field = (argument as? UReferenceExpression)?.resolve() as? PsiField ?: return false
        val className = field.containingClass?.qualifiedName ?: return false
        return field.name == "ACTION_FOCUS" &&
            (className == ACCESSIBILITY_NODE_INFO || className == ACCESSIBILITY_NODE_ACTION)
    }

    private fun isFocusEventType(argument: UExpression?): Boolean {
        val field = (argument as? UReferenceExpression)?.resolve() as? PsiField ?: return false
        val className = field.containingClass?.qualifiedName ?: return false
        return field.name == "TYPE_VIEW_FOCUSED" && className == ACCESSIBILITY_EVENT
    }

    private fun reportIncident(context: JavaContext, node: UElement) {
        val message =
            "Forcing accessibility focus can interfere with screen readers and lead to an inconsistent user experience."
        context.report(
            Incident(
                issue = ACCESSIBILITY_FOCUS,
                scope = node,
                location = context.getLocation(node),
                message = message,
            )
        )
    }
}