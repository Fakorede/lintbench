package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.getUMethod
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastVisitor
import org.jetbrains.uast.getParentOfType

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil equality check",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. Using identity
                equality (`==` / `===`) or calling `equals` on a type that does not implement
                structural equality can produce incorrect diff results and visual artifacts.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val DIFF_UTIL_CALLBACK = "androidx.recyclerview.widget.DiffUtil.Callback"
        private const val METHOD_NAME = "areContentsTheSame"
    }

    override fun getApplicableUElementTypes() = listOf(UMethod::class.java)

    override fun createUElementHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitMethod(node: UMethod) {
            if (node.name != METHOD_NAME) return

            val containingClass = node.getParentOfType<UClass, false>(UClass::class.java)
                ?.javaPsi
                ?: node.containingClass
                ?: return
            if (!context.evaluator.inheritsFrom(containingClass, DIFF_UTIL_CALLBACK, false)) return

            val body = node.uastBody ?: return
            body.accept(SuspiciousEqualityVisitor(context))
        }
    }

    private class SuspiciousEqualityVisitor(
        private val context: JavaContext
    ) : UastVisitor {

        override fun visitElement(node: UElement): Boolean {
            return false
        }

        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
            if (node.operator == UastBinaryOperator.IDENTITY_EQUALS ||
                node.operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
            ) {
                report(node)
            }
            return false
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            if (node.methodName == "equals" && node.valueArgumentCount == 1) {
                val receiver = node.receiver
                val receiverType = receiver?.getExpressionType()
                if (receiverType != null && !hasStructuralEquals(context, receiverType)) {
                    report(node)
                }
            }
            return false
        }

        private fun report(node: UElement) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Suspicious equality check in `areContentsTheSame`; use structural equality, not identity equality."
            )
        }

        private fun hasStructuralEquals(context: JavaContext, type: PsiType): Boolean {
            val psiClass = context.evaluator.getTypeClass(type) ?: return false

            var cls: PsiClass? = psiClass
            while (cls != null) {
                val className = cls.qualifiedName
                if (className == "java.lang.Object" || className == "kotlin.Any") {
                    // Reached the root; no override found.
                    return false
                }

                for (method in cls.methods) {
                    if (method.name == "equals" && method.parameterList.parametersCount == 1) {
                        val paramType = method.parameterList.parameters[0]?.type?.canonicalText
                        if (paramType == "java.lang.Object" ||
                            paramType == "java.lang.Object?" ||
                            paramType == "kotlin.Any" ||
                            paramType == "kotlin.Any?"
                        ) {
                            return true
                        }
                    }
                }

                cls = cls.superClass
            }

            return false
        }
    }
}