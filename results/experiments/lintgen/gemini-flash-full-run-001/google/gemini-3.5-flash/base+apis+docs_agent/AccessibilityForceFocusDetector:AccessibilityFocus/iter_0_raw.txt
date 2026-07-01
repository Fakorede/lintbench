package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Forcing accessibility focus interferes with screen readers and gives an \
                inconsistent user experience, especially across apps.
            """.trimIndent(),
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AccessibilityForceFocusDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "performAccessibilityAction",
            "performAction",
            "sendAccessibilityEvent",
            "obtain"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val name = method.name
        val evaluator = context.evaluator

        when (name) {
            "performAccessibilityAction", "performAction" -> {
                if (evaluator.isMemberInSubClassOf(method, "android.view.View", false) ||
                    evaluator.isMemberInSubClassOf(method, "android.view.accessibility.AccessibilityNodeInfo", false) ||
                    evaluator.isMemberInSubClassOf(method, "android.view.accessibility.AccessibilityNodeProvider", false)
                ) {
                    val args = node.valueArguments
                    if (args.isNotEmpty()) {
                        val firstArg = args[0]
                        val value = firstArg.evaluate()
                        if (value is Int) {
                            if (value == 64) { // AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
                                report(context, node)
                            }
                        } else {
                            val argString = firstArg.asSourceString()
                            if (argString.contains("ACTION_ACCESSIBILITY_FOCUS") || argString == "64" || argString == "0x40") {
                                report(context, node)
                            }
                        }
                    }
                }
            }
            "sendAccessibilityEvent" -> {
                if (evaluator.isMemberInSubClassOf(method, "android.view.View", false) ||
                    evaluator.isMemberInSubClassOf(method, "android.view.ViewParent", false)
                ) {
                    val args = node.valueArguments
                    if (args.isNotEmpty()) {
                        val firstArg = args[0]
                        val value = firstArg.evaluate()
                        if (value is Int) {
                            if (value == 32768) { // AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                                report(context, node)
                            }
                        } else {
                            val argString = firstArg.asSourceString()
                            if (argString.contains("TYPE_VIEW_ACCESSIBILITY_FOCUSED") || argString == "32768" || argString == "0x8000") {
                                report(context, node)
                            }
                        }
                    }
                }
            }
            "obtain" -> {
                if (evaluator.isMemberInSubClassOf(method, "android.view.accessibility.AccessibilityEvent", false)) {
                    val args = node.valueArguments
                    if (args.isNotEmpty()) {
                        val firstArg = args[0]
                        val value = firstArg.evaluate()
                        if (value is Int) {
                            if (value == 32768) { // AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                                report(context, node)
                            }
                        } else {
                            val argString = firstArg.asSourceString()
                            if (argString.contains("TYPE_VIEW_ACCESSIBILITY_FOCUSED") || argString == "32768" || argString == "0x8000") {
                                report(context, node)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Forcing accessibility focus is not recommended as it interferes with screen readers"
        )
    }
}