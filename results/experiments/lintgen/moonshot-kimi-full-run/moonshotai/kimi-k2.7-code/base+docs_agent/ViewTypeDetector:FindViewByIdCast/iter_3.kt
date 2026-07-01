package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType

private const val VIEW_CLASS = "android.view.View"

class ViewTypeDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O, the `findViewById` signature switched to using generics, which means
                that most of the time you can leave out explicit casts and just assign the result of
                the `findViewById` call to variables of specific view classes.

                However, due to language changes between Java 7 and 8, this change may cause code to
                not compile without explicit casts. This lint check looks for these scenarios and
                suggests casts to be added now such that the code will continue to compile if the
                language level is updated to 1.8.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        if (context.psiFile !is PsiJavaFile) return null
        val level = context.project.javaLanguageLevel
        if (level != null && level.isAtLeast(LanguageLevel.JDK_1_8)) return null

        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                checkCall(context, node)
            }
        }
    }

    private fun checkCall(context: JavaContext, node: UCallExpression) {
        if (node.typeArguments.isNotEmpty()) return

        val method = node.resolve() ?: return
        if (method.name != "findViewById") return

        val params = method.parameterList.parameters
        if (params.size != 1 || params[0].type != PsiType.INT) return

        val returnType = method.returnType as? PsiClassType ?: return
        val typeParameter = returnType.resolve() as? PsiTypeParameter ?: return
        val boundClass = typeParameter.extendsListTypes.firstOrNull()?.resolve() ?: return
        if (!InheritanceUtil.isInheritor(boundClass, VIEW_CLASS, false)) return

        val expectedType = getExpectedType(node) ?: return
        val expectedClass = (expectedType as? PsiClassType)?.resolve() ?: return
        if (expectedClass.qualifiedName == VIEW_CLASS) return
        if (!InheritanceUtil.isInheritor(expectedClass, VIEW_CLASS, true)) return

        val source = node.sourcePsi?.text
        if (source != null) {
            val fix = LintFix.create()
                .replace()
                .range(context.getLocation(node))
                .text(source)
                .with("(${expectedType.canonicalText}) $source")
                .build()

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Add explicit cast to ${expectedClass.name}",
                fix
            )
        } else {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Add explicit cast to ${expectedClass.name}"
            )
        }
    }

    private fun getExpectedType(node: UCallExpression): PsiType? {
        var current: UExpression = node
        var parent = current.uastParent ?: return null

        while (true) {
            when (parent) {
                is UParenthesizedExpression -> {
                    current = parent
                }
                is UQualifiedReferenceExpression -> {
                    if (parent.selector == current) {
                        current = parent
                    } else {
                        return null
                    }
                }
                is UBinaryExpressionWithType -> {
                    return null
                }
                is UVariable -> {
                    return parent.type
                }
                is UBinaryExpression -> {
                    if (parent.operator == UastBinaryOperator.ASSIGN && parent.rightOperand == current) {
                        return parent.leftOperand.getExpressionType()
                    }
                    return null
                }
                is UReturnExpression -> {
                    return parent.getParentOfType<UMethod>(UMethod::class.java)?.returnType
                }
                is UCallExpression -> {
                    val index = parent.valueArguments.indexOf(current)
                    if (index >= 0) {
                        val resolved = parent.resolve() ?: return null
                        val parameters = resolved.parameterList.parameters
                        if (index < parameters.size) {
                            return parameters[index].type
                        }
                    }
                    return null
                }
                else -> return null
            }
            parent = current.uastParent ?: return null
        }
    }
}