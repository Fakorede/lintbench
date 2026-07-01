package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

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
            priority = 4,
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

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement
    ) {
        val member = referenced as? PsiMember ?: return
        if (context.evaluator.isMemberInClass(member, "android.view.accessibility.AccessibilityEvent") ||
            context.evaluator.isMemberInClass(member, "androidx.core.view.accessibility.AccessibilityEventCompat")
        ) {
            context.report(
                ISSUE,
                reference,
                context.getLocation(reference),
                "Sending or populating `TYPE_WINDOW_STATE_CHANGED` events is strongly discouraged. " +
                        "Prefer using system-provided widgets, setting titles, or implementing accessibility node info."
            )
        }
    }
}