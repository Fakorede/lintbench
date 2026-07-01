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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class EmptySuperDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor) return

                val superMethod = context.evaluator.getSuperMethod(node) ?: return
                if (!superMethod.hasAnnotation(ANNOTATION_NAME)) return

                node.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(call: UCallExpression): Boolean {
                        val receiver = call.receiver
                        if (receiver is USuperExpression) {
                            val resolved = call.resolve() as? PsiMethod
                            if (resolved == superMethod ||
                                (call.methodName == superMethod.name &&
                                    call.valueArgumentCount == superMethod.parameterList.parameters.size)
                            ) {
                                context.report(
                                    ISSUE,
                                    call,
                                    context.getLocation(call),
                                    "Overriding methods should not call a super implementation annotated with @EmptySuper"
                                )
                            }
                        }
                        return false
                    }
                })
            }
        }

    private fun PsiMethod.hasAnnotation(simpleName: String): Boolean {
        return modifierList?.annotations?.any { annotation ->
            val qualifiedName = annotation.qualifiedName
            qualifiedName == simpleName || qualifiedName?.endsWith(".$simpleName") == true
        } == true
    }

    companion object {
        private const val ANNOTATION_NAME = "EmptySuper"

        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                Methods annotated with @EmptySuper should not be invoked via super by overriding methods.
                The super implementation is either empty or contains code that should not run when the method is overridden.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                EmptySuperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}