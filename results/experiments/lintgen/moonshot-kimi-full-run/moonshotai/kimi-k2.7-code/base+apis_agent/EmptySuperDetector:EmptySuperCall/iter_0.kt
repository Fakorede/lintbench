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
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class EmptySuperDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitMethod(node: UMethod) {
            if (node.isConstructor) return
            val superMethod = context.evaluator.getSuperMethod(node) ?: return
            if (!superMethod.hasEmptySuperAnnotation()) return

            node.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(call: UCallExpression): Boolean {
                    if (call.receiver is USuperExpression &&
                        call.methodName == superMethod.name &&
                        call.valueArgumentCount == superMethod.parameterList.parametersCount
                    ) {
                        context.report(
                            ISSUE,
                            call,
                            context.getLocation(call),
                            "Overriding methods should not call a super implementation annotated with @EmptySuper"
                        )
                    }
                    return false
                }
            })
        }
    }

    private fun PsiMethod.hasEmptySuperAnnotation(): Boolean {
        return modifierList?.annotations?.any {
            val name = it.qualifiedName
            name == "EmptySuper" || name?.endsWith(".EmptySuper") == true
        } == true
    }

    companion object {
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