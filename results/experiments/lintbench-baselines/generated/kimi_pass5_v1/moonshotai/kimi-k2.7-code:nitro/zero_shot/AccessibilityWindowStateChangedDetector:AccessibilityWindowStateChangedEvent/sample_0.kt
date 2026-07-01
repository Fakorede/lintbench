package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiField
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.resolve

class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AccessibilityWindowStateChangedEvent",
            briefDescription = "Avoid AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED",
            explanation = """
                Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code is strongly discouraged.
                Prefer to use or extend system-provided widgets that are far down Android's class hierarchy.
                If you must extend `View` or `Canvas` directly, set UI metadata via `Activity.setTitle`,
                `ViewCompat.setAccessibilityPaneTitle`, or `ViewCompat.setAccessibilityLiveRegion`;
                implement `View.onInitializeAccessibilityNodeInfo`; and (for very specialized custom controls)
                implement `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy.
                These approaches allow accessibility services to inspect the view hierarchy, rather than relying on incomplete information provided by events.
                Events like `TYPE_WINDOW_STATE_CHANGED` will be sent automatically when updating this metadata,
                so manually sending this event may result in duplicate events or the event being ignored.
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

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitReferenceExpression(node: UReferenceExpression) {
                val resolved = node.resolve()
                if (resolved is PsiField && isWindowStateChangedField(resolved)) {
                    context.report(
                        issue = ISSUE,
                        scope = node,
                        location = context.getLocation(node),
                        message = "Avoid using AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED; prefer accessibility metadata and system-provided widgets instead."
                    )
                }
            }
        }
    }

    private fun isWindowStateChangedField(field: PsiField): Boolean {
        if (field.name != "TYPE_WINDOW_STATE_CHANGED") return false
        val className = field.containingClass?.qualifiedName ?: return false
        return className == "android.view.accessibility.AccessibilityEvent"
                || className == "android.support.v4.view.accessibility.AccessibilityEventCompat"
                || className == "androidx.core.view.accessibility.AccessibilityEventCompat"
    }
}