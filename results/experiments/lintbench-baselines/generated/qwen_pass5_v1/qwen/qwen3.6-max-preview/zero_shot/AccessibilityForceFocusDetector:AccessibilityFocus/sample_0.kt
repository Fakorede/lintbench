package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiField
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.tryResolve

class AccessibilityForceFocusDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val methodName = node.methodName ?: return
                if (methodName !in TARGET_METHODS) return

                val method = node.resolve() ?: return
                val owner = method.containingClass?.qualifiedName ?: return
                val args = node.valueArguments
                if (args.isEmpty()) return

                val firstArg = args[0]
                val isForcingFocus = when {
                    methodName == "sendAccessibilityEvent" && owner == "android.view.View" ->
                        matchesConstant(firstArg, "android.view.accessibility.AccessibilityEvent", "TYPE_VIEW_ACCESSIBILITY_FOCUSED", 32768)
                    methodName == "performAccessibilityAction" && owner == "android.view.View" ->
                        matchesConstant(firstArg, "android.view.accessibility.AccessibilityNodeInfo", "ACTION_ACCESSIBILITY_FOCUS", 64) ||
                        matchesConstant(firstArg, "androidx.core.view.accessibility.AccessibilityNodeInfoCompat", "ACTION_ACCESSIBILITY_FOCUS", 64)
                    methodName == "performAction" && (owner == "android.view.accessibility.AccessibilityNodeInfo" || owner == "androidx.core.view.accessibility.AccessibilityNodeInfoCompat") ->
                        matchesConstant(firstArg, "android.view.accessibility.AccessibilityNodeInfo", "ACTION_ACCESSIBILITY_FOCUS", 64) ||
                        matchesConstant(firstArg, "androidx.core.view.accessibility.AccessibilityNodeInfoCompat", "ACTION_ACCESSIBILITY_FOCUS", 64)
                    else -> false
                }

                if (isForcingFocus) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Do not force accessibility focus; it interferes with screen readers and creates an inconsistent user experience"
                    )
                }
            }
        }
    }

    private fun matchesConstant(expr: UExpression, className: String, fieldName: String, expectedValue: Int): Boolean {
        val resolved = expr.tryResolve()
        if (resolved is PsiField) {
            val qName = resolved.containingClass?.qualifiedName
            if (qName == className && resolved.name == fieldName) return true
        }
        val value = expr.evaluate()
        return value is Int && value == expectedValue
    }

    companion object {
        private val TARGET_METHODS = setOf("sendAccessibilityEvent", "performAccessibilityAction", "performAction")

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = "Forcing accessibility focus interferes with screen readers and gives an inconsistent user experience, especially across apps.",
            category = Category.ACCESSIBILITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}