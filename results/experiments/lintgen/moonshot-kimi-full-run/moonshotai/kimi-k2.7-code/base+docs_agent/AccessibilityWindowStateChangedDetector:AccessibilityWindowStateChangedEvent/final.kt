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
import com.intellij.psi.PsiMember
import org.jetbrains.uast.UReferenceExpression

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    override fun getApplicableReferenceNames(): List<String> =
        listOf("TYPE_WINDOW_STATE_CHANGED")

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement
    ) {
        if (referenced !is PsiMember ||
            !context.evaluator.isMemberInClass(
                referenced,
                "android.view.accessibility.AccessibilityEvent"
            )
        ) {
            return
        }

        context.report(
            ISSUE,
            reference,
            context.getLocation(reference),
            "Avoid populating or sending TYPE_WINDOW_STATE_CHANGED events; rely on system-provided accessibility metadata instead"
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = "Use of accessibility window state change events",
            explanation = """
                Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly discouraged.
                Instead, prefer to use or extend system-provided widgets that are as far down Android's class
                hierarchy as possible. If you must extend `View` or `Canvas` directly, set accessibility metadata
                via `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or
                `ViewCompat.setAccessibilityLiveRegion`, implement `View.onInitializeAccessibilityNodeInfo`, and
                (for very specialized custom controls) implement `View.getAccessibilityNodeProvider` to provide a
                virtual view hierarchy. Events like `TYPE_WINDOW_STATE_CHANGED` will be sent automatically when
                updating this metadata, so manually sending this event can result in duplicate or ignored events.
                """.trimIndent(),
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityWindowStateChangedDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}