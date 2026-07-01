package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Element
import java.util.EnumSet

class AccessibilityForceFocusDetector : Detector(), Detector.UastScanner, XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.",
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE)
            )
        )

        private const val REQUEST_FOCUS_TAG = "requestFocus"
        private const val TYPE_VIEW_FOCUSED = "android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED"
        private const val ACTION_ACCESSIBILITY_FOCUS = "android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS"
    }

    override fun getApplicableElements(): Collection<String>? = listOf(REQUEST_FOCUS_TAG)

    override fun visitElement(context: XmlContext, element: Element) {
        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "Forcing accessibility focus via `<requestFocus>` interferes with screen readers. Remove this element to allow the system to manage focus naturally."
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "sendAccessibilityEvent",
        "performAccessibilityAction",
        "performAction"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val args = node.valueArguments
        if (args.isEmpty()) return

        val firstArg = args[0]
        val resolved = (firstArg as? UReferenceExpression)?.resolve() as? PsiField
        val qualifiedName = resolved?.containingClass?.qualifiedName + "." + resolved?.name

        val isFocusEvent = method.name == "sendAccessibilityEvent" && qualifiedName == TYPE_VIEW_FOCUSED
        val isFocusAction = (method.name == "performAccessibilityAction" || method.name == "performAction") && qualifiedName == ACTION_ACCESSIBILITY_FOCUS

        if (isFocusEvent || isFocusAction) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps."
            )
        }
    }
}