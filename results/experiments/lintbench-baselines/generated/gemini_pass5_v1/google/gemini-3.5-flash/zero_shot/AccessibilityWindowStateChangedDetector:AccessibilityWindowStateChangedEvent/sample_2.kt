package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiField
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    override fun getApplicableReferenceNames(): List<String> {
        return listOf("TYPE_WINDOW_STATE_CHANGED")
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                val resolved = node.resolve()
                if (resolved is PsiField) {
                    val containingClass = resolved.containingClass
                    if (containingClass?.qualifiedName == "android.view.accessibility.AccessibilityEvent") {
                        if (!isSafeContext(node)) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Sending or populating `TYPE_WINDOW_STATE_CHANGED` events is strongly discouraged."
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isSafeContext(node: USimpleNameReferenceExpression): Boolean {
        var current: UElement = node
        while (true) {
            val parent = current.uastParent ?: break
            if (parent is UQualifiedReferenceExpression) {
                current = parent
                continue
            }
            if (parent is UBinaryExpression) {
                val op = parent.operator.text
                if (op == "==" || op == "!=" || op == "===" || op == "!==") {
                    return true
                }
            }
            val parentName = parent.javaClass.name
            if (parentName.contains("Switch") || parentName.contains("When")) {
                return true
            }
            break
        }
        return false
    }

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
                implement `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy. \
                These approaches allow accessibility services to inspect the view hierarchy, rather than relying \
                on incomplete information provided by events. Events like `TYPE_WINDOW_STATE_CHANGED` will be \
                sent automatically when updating this metadata, and so trying to manually send this event will \
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
}