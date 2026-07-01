package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiUtil
import org.jetbrains.uast.UAssignmentExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCastExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getParentOfType

class ViewTypeDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUElementTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUElementHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (!context.file.name.endsWith(".java")) {
                    return
                }

                val method: PsiMethod = node.resolve() ?: return
                if (method.name != "findViewById") {
                    return
                }

                // Only the generic findViewById signature (API 26+) causes this problem.
                if (method.typeParameters.isEmpty()) {
                    return
                }

                // Explicit type arguments make a cast unnecessary.
                if (node.typeArgumentCount > 0) {
                    return
                }

                val parent = node.uastParent?.skipParentheses()

                // Already explicitly cast?
                if (parent is UCastExpression || parent is UBinaryExpressionWithType) {
                    return
                }

                val targetType = findTargetType(context, node) ?: return
                val targetClass = PsiUtil.resolveClassInClassTypeOnly(targetType) ?: return

                // Only warn when the expected type is a concrete View subclass.
                if (!context.evaluator.extendsFrom(targetClass, VIEW_CLASS, true)) {
                    return
                }

                val targetName = targetType.canonicalText
                val callSource = node.asSourceString()
                val message = String.format(MESSAGE, targetName)
                val fix: LintFix = LintFix.create()
                    .name("Add explicit cast")
                    .replace()
                    .range(context.getLocation(node))
                    .with("($targetName) $callSource")
                    .shortenNames()
                    .build()

                context.report(ISSUE, node, context.getLocation(node), message, fix)
            }
        }
    }

    private fun findTargetType(context: JavaContext, call: UCallExpression): PsiType? {
        val parent = call.uastParent?.skipParentheses() ?: return null

        return when (parent) {
            is UVariable -> parent.type
            is UAssignmentExpression -> parent.leftOperand.getExpressionType()
            is UReturnExpression -> parent.getParentOfType(UMethod::class.java)?.returnType
            is UCallExpression -> {
                val index = parent.valueArguments.indexOf(call)
                if (index < 0) {
                    return null
                }
                val resolved = parent.resolve() ?: return null
                val params = resolved.parameterList.parameters
                if (index < params.size) params[index].type else null
            }
            else -> null
        }
    }

    private fun UElement?.skipParentheses(): UElement? {
        var element = this
        while (element is UParenthesizedExpression) {
            element = element.uastParent
        }
        return element
    }

    companion object {
        private const val VIEW_CLASS = "android.view.View"
        private const val MESSAGE =
            "The generic `findViewById` method does not infer the target type from the " +
                "assignment context under a Java 7 type inferencer. Add an explicit cast to %1\$s " +
                "now so that this code will continue to compile if the language level is updated to 1.8."

        @JvmField
        val ISSUE = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O (API 26) `findViewById` changed to a generic method. When the
                method result is used in a context that expects a concrete `View` subclass, the
                Java 7 type inferencer cannot infer that type from the surrounding assignment,
                return, or argument context, so the code will not compile without an explicit
                cast. Add the cast now to keep the code compiling when the language level is
                updated to 1.8.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}