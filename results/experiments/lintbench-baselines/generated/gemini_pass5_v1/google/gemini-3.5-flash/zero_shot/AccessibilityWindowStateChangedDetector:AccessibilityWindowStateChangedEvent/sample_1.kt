package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiField
import org.jetbrains.uast.UAssignExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator

class AccessibilityWindowStateChangedDetector : Detector(), Detector.UastScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = "Use of accessibility window state change events",
            explanation = """
                Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly discouraged. \
                Instead, prefer to use or extend system-provided widgets that are as far down Android's \
                class hierarchy as possible. System-provided widgets that are far down the hierarchy already \
                have most of the accessibility capabilities your app needs. If you must extend `View` or `Canvas` \
                directly, then still prefer to: set UI metadata by calling `Activity.setTitle`, \
                `ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`; \
                implement `View.onInitializeAccessibilityNodeInfo`; and (for very specialized custom controls) \
                implement `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy. These \
                approaches allow accessibility services to inspect the view hierarchy, rather than relying on \
                incomplete information provided by events. Events like `TYPE_WINDOW_STATE_CHANGED` will be sent \
                automatically when updating this metadata, and so trying to manually send this event will \
                result in duplicate events, or the event may be ignored entirely.
            """.trimIndent(),
            category = Category.A11Y,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityWindowStateChangedDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableReferenceNames(): List<String> {
        return listOf("TYPE_WINDOW_STATE_CHANGED")
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression) {
        val resolved = reference.resolve() as? PsiField ?: return
        val containingClass = resolved.containingClass ?: return
        if (containingClass.qualifiedName == "android.view.accessibility.AccessibilityEvent" &&
            resolved.name == "TYPE_WINDOW_STATE_CHANGED"
        ) {
            var current: UElement? = reference
            while (current != null) {
                val parent = current.uastParent ?: break
                if (parent is org.jetbrains.uast.UMethod || 
                    parent is org.jetbrains.uast.UClass || 
                    parent is org.jetbrains.uast.UFile
                ) {
                    break
                }

                if (parent is UCallExpression) {
                    if (parent.valueArguments.contains(current)) {
                        report(context, reference)
                        return
                    }
                } else if (parent is UAssignExpression) {
                    if (parent.rValue == current) {
                        report(context, reference)
                        return
                    }
                } else if (parent is UVariable) {
                    if (parent.uastInitializer == current) {
                        report(context, reference)
                        return
                    }
                } else if (parent is UBinaryExpression) {
                    val op = parent.operator
                    if (op == UastBinaryOperator.EQUALS || 
                        op == UastBinaryOperator.NOT_EQUALS ||
                        op == UastBinaryOperator.IDENTITY_EQUALS || 
                        op == UastBinaryOperator.IDENTITY_NOT_EQUALS
                    ) {
                        // This is a comparison: do not report
                        return
                    }
                }
                current = parent
            }
        }
    }

    private fun report(context: JavaContext, reference: UReferenceExpression) {
        context.report(
            ISSUE,
            reference,
            context.getLocation(reference),
            "Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly discouraged."
        )
    }
}