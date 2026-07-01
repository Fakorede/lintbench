package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            AccessibilityWindowStateChangedDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

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
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableReferenceNames(): List<String> {
        return listOf("TYPE_WINDOW_STATE_CHANGED")
    }

    override fun visitReference(
        context: JavaContext, reference: UReferenceExpression, referenced: PsiElement,
    ) {
        val evaluator = context.evaluator
        if (evaluator.isMemberInClass(referenced, "android.view.accessibility.AccessibilityEvent") ||
            evaluator.isMemberInClass(referenced, "androidx.core.view.accessibility.AccessibilityEventCompat")
        ) {
            context.report(
                ISSUE,
                reference,
                context.getLocation(reference),
                "Do not manually send or populate `TYPE_WINDOW_STATE_CHANGED` events. " +
                        "Instead, use `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or " +
                        "`ViewCompat.setAccessibilityLiveRegion` to let the platform send these events automatically."
            )
        }
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("sendAccessibilityEvent", "sendAccessibilityEventUnchecked", "obtain")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        val name = method.name
        if (name == "sendAccessibilityEvent" || name == "sendAccessibilityEventUnchecked") {
            if (evaluator.isMemberInSubClassOf(method, "android.view.View", false)) {
                val arg = node.valueArguments.firstOrNull() ?: return
                val evaluated = arg.evaluate()
                if (evaluated is Int && evaluated == 32) {
                    if (arg !is UReferenceExpression) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Do not manually send or populate `TYPE_WINDOW_STATE_CHANGED` events. " +
                                    "Instead, use `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or " +
                                    "`ViewCompat.setAccessibilityLiveRegion` to let the platform send these events automatically."
                        )
                    }
                }
            }
        } else if (name == "obtain") {
            if (evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityEvent") ||
                evaluator.isMemberInClass(method, "androidx.core.view.accessibility.AccessibilityEventCompat")
            ) {
                val arg = node.valueArguments.firstOrNull() ?: return
                val evaluated = arg.evaluate()
                if (evaluated is Int && evaluated == 32) {
                    if (arg !is UReferenceExpression) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Do not manually send or populate `TYPE_WINDOW_STATE_CHANGED` events. " +
                                    "Instead, use `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or " +
                                    "`ViewCompat.setAccessibilityLiveRegion` to let the platform send these events automatically."
                        )
                    }
                }
            }
        }
    }
}