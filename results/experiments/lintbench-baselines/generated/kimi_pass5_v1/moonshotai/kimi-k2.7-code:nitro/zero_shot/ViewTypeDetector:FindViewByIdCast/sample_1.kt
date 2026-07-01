package com.android.tools.lint.checks

import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType
import java.util.EnumSet

class ViewTypeDetector : Detector(), SourceCodeScanner {

    override fun getApplicableCallNames(): List<String> = listOf(FIND_VIEW_BY_ID)

    override fun visitCallExpression(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (node.valueArgumentCount != 1) return
        if (context.mainProject.buildTarget.apiLevel < 26) return

        val evaluator = context.evaluator
        val containingClass = method.containingClass ?: return
        if (!isFindViewByIdOwner(evaluator, containingClass)) return

        // The Android O Activity/View signatures are generic; explicit casts are usually
        // not needed there. However, non-generic overrides (such as older support
        // library implementations) still return View, which can cause Java 8 compile
        // failures when assigned to a specific View subclass.
        if (method.typeParameters.isNotEmpty()) return

        val parent = node.uastParent
        if (parent is UBinaryExpressionWithType) return

        val expectedType = getExpectedType(context, node) ?: return
        if (expectedType == PsiType.VOID) return
        if (!evaluator.extendsClass(expectedType, CLASS_VIEW, false)) return

        val message = "Add explicit cast to ${expectedType.presentableText}"
        val fix = createAddCastFix(context, node, expectedType)
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            message,
            fix
        )
    }

    private fun isFindViewByIdOwner(evaluator: JavaEvaluator, cls: PsiClass): Boolean {
        return evaluator.extendsClass(cls, CLASS_ACTIVITY, false) ||
            evaluator.extendsClass(cls, CLASS_VIEW, false) ||
            evaluator.extendsClass(cls, CLASS_FRAGMENT_ACTIVITY, false) ||
            evaluator.extendsClass(cls, CLASS_FRAGMENT_ACTIVITYX, false)
    }

    private fun getExpectedType(context: JavaContext, node: UCallExpression): PsiType? {
        val parent = node.uastParent ?: return null
        return when (parent) {
            is UBinaryExpression -> {
                if (parent.operator == UastBinaryOperator.ASSIGN) {
                    parent.leftOperand.getExpressionType()
                } else {
                    null
                }
            }
            is UVariable -> parent.type
            is UReturnExpression -> parent.getParentOfType(UMethod::class.java)?.returnType
            is UCallExpression -> parent.getParameterForArgument(node)?.type
            else -> null
        }
    }

    private fun createAddCastFix(context: JavaContext, node: UCallExpression, type: PsiType): LintFix? {
        val source = node.sourcePsi?.text ?: node.asSourceString()
        val replacement = if (context.file.extension == "java") {
            "(${type.presentableText}) $source"
        } else {
            "$source as ${type.presentableText}"
        }
        return LintFix.create()
            .replace()
            .name("Add explicit cast")
            .with(replacement)
            .range(context.getLocation(node))
            .reformat(true)
            .build()
    }

    companion object {
        private const val ID = "FindViewByIdCast"
        private const val SUMMARY = "Add Explicit Cast"
        private const val FIND_VIEW_BY_ID = "findViewById"
        private const val CLASS_VIEW = "android.view.View"
        private const val CLASS_ACTIVITY = "android.app.Activity"
        private const val CLASS_FRAGMENT_ACTIVITY = "android.support.v4.app.FragmentActivity"
        private const val CLASS_FRAGMENT_ACTIVITYX = "androidx.fragment.app.FragmentActivity"
        private const val PRIORITY = 6

        private val EXPLANATION = """
            In Android O, the `findViewById` signature switched to using generics, which
            means that most of the time you can leave out explicit casts and just assign
            the result of the `findViewById` call to variables of specific view classes.

            However, due to language changes between Java 7 and 8, this change may cause
            code to not compile without explicit casts. This lint check looks for these
            scenarios and suggests casts to be added now such that the code will continue
            to compile if the language level is updated to 1.8.
        """.trimIndent()

        private val IMPLEMENTATION = Implementation(
            ViewTypeDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE_SCOPE)
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = ID,
            briefDescription = SUMMARY,
            explanation = EXPLANATION,
            category = Category.CORRECTNESS,
            priority = PRIORITY,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }
}