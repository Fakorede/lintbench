package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastCallKind

class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            AccessibilityForceFocusDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        private const val ACTION_ACCESSIBILITY_FOCUS = 64
        private const val ACTION_CLEAR_ACCESSIBILITY_FOCUS = 128
        private const val TYPE_VIEW_FOCUSED = 8
        private const val TYPE_VIEW_ACCESSIBILITY_FOCUSED = 0x00004000

        private val FOCUS_METHODS = setOf(
            "performAction",
            "performAccessibilityAction",
            "sendAccessibilityEvent",
        )

        private val FOCUS_CONSTANT_NAMES = setOf(
            "ACTION_ACCESSIBILITY_FOCUS",
            "ACTION_CLEAR_ACCESSIBILITY_FOCUS",
            "TYPE_VIEW_FOCUSED",
            "TYPE_VIEW_ACCESSIBILITY_FOCUSED",
        )

        private val FOCUS_CLASSES = setOf(
            "android.view.accessibility.AccessibilityEvent",
            "android.view.accessibility.AccessibilityNodeInfo",
            "android.support.v4.view.accessibility.AccessibilityNodeInfoCompat",
            "androidx.core.view.accessibility.AccessibilityNodeInfoCompat",
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AccessibilityFocus",
            briefDescription = "Forcing accessibility focus",
            explanation = """
                Accessibility services and screen readers manage focus on behalf of the user. \
                Explicitly forcing an accessibility focus—by calling \
                performAction(ACTION_ACCESSIBILITY_FOCUS), \
                performAccessibilityAction(ACTION_ACCESSIBILITY_FOCUS), or sending \
                TYPE_VIEW_FOCUSED / TYPE_VIEW_ACCESSIBILITY_FOCUSED events—interferes \
                with TalkBack and other assistive technologies and creates an inconsistent \
                experience across apps. Avoid forcing focus and let the system and user \
                control it instead.
            """.trimIndent(),
            category = Category.A11Y,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? = FOCUS_METHODS.toList()

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (node.kind != UastCallKind.METHOD_CALL) {
            return
        }

        val args = node.valueArguments
        if (args.isEmpty()) {
            return
        }

        val firstArg = args[0]
        if (isFocusConstantReference(firstArg)) {
            // Handled by visitSimpleNameReferenceExpression to avoid duplicate reports.
            return
        }

        val value = ConstantEvaluator.evaluate(context, firstArg) as? Number ?: return
        val intValue = value.toInt()

        when (method.name) {
            "performAction", "performAccessibilityAction" -> {
                if (!isAccessibilityActionMethod(context, method)) {
                    return
                }
                if (intValue == ACTION_ACCESSIBILITY_FOCUS ||
                    intValue == ACTION_CLEAR_ACCESSIBILITY_FOCUS
                ) {
                    report(context, node)
                }
            }
            "sendAccessibilityEvent" -> {
                if (!context.evaluator.isMemberInClass(method, "android.view.View")) {
                    return
                }
                if (intValue == TYPE_VIEW_FOCUSED ||
                    intValue == TYPE_VIEW_ACCESSIBILITY_FOCUSED
                ) {
                    report(context, node)
                }
            }
        }
    }

    override fun getApplicableReferenceNames(): List<String>? = FOCUS_CONSTANT_NAMES.toList()

    override fun visitSimpleNameReferenceExpression(
        context: JavaContext,
        node: USimpleNameReferenceExpression,
    ) {
        val parent = node.uastParent
        if (parent is UImportStatement) {
            return
        }

        if (node.identifier !in FOCUS_CONSTANT_NAMES) {
            return
        }

        val field = node.resolve() as? PsiField ?: return
        val className = field.containingClass?.qualifiedName ?: return
        if (className !in FOCUS_CLASSES) {
            return
        }

        // Only report direct references that are passed as the first argument to one of the
        // known focus-forcing methods. Other usages (comparisons, assignments, etc.) are not.
        val call = parent as? UCallExpression ?: return
        if (call.methodName !in FOCUS_METHODS) {
            return
        }
        if (call.valueArguments.firstOrNull() != node) {
            return
        }

        report(context, node)
    }

    private fun isAccessibilityActionMethod(context: JavaContext, method: PsiMethod): Boolean {
        return context.evaluator.isMemberInClass(method, "android.view.accessibility.AccessibilityNodeInfo") ||
            context.evaluator.isMemberInClass(
                method,
                "android.support.v4.view.accessibility.AccessibilityNodeInfoCompat",
            ) ||
            context.evaluator.isMemberInClass(
                method,
                "androidx.core.view.accessibility.AccessibilityNodeInfoCompat",
            ) ||
            context.evaluator.isMemberInClass(method, "android.view.View")
    }

    private fun isFocusConstantReference(expression: UExpression): Boolean {
        val ref = expression as? org.jetbrains.uast.UReferenceExpression ?: return false
        val field = ref.resolve() as? PsiField ?: return false
        return field.name in FOCUS_CONSTANT_NAMES &&
            field.containingClass?.qualifiedName in FOCUS_CLASSES
    }

    private fun report(context: JavaContext, node: UElement) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Forcing accessibility focus can interfere with screen readers and lead to an inconsistent user experience",
        )
    }
}