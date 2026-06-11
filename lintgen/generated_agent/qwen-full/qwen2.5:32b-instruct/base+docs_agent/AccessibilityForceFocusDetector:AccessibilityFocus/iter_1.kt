package com.android.tools.lint.checks

import com.android.SdkConstants.*
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        val FORCED_ACCESSIBILITY_FOCUS = Issue.create(
            id = "ForcedAccessibilityFocus",
            briefDescription = "Avoid forcing accessibility focus",
            explanation = """
                Forcing accessibility focus can interfere with screen readers and give an inconsistent user experience, especially across apps.
            """,
            category = Category.USABILITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val methodIdentifier = node.methodIdentifier ?: return
                if (methodIdentifier.name == "performAccessibilityAction") {
                    val argument = node.valueArguments.firstOrNull()?.uastValue as? ULiteralExpression ?: return

                    if (argument.value == ACTION_FOCUS) {
                        context.report(
                            FORCED_ACCESSIBILITY_FOCUS,
                            node,
                            context.getLocation(node),
                            "Avoid forcing accessibility focus"
                        )
                    }
                }
            }
        }
    }
}