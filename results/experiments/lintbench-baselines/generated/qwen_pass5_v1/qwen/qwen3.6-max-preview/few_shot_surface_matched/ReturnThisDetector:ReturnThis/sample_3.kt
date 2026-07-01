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
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UastAnnotationUsageType
import org.jetbrains.uast.getParentOfType

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = "Methods annotated with `@ReturnThis` (usually in the super method that this method is overriding) should also `return this`.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(ReturnThisDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
        private const val ANNOTATION_NAME = "ReturnThis"
    }

    private val methodsNeedingReturnThis = mutableSetOf<UMethod>()

    override fun beforeCheckFile(context: JavaContext) {
        methodsNeedingReturnThis.clear()
    }

    override fun applicableAnnotations(): List<String> = listOf(ANNOTATION_NAME)

    override fun isApplicableAnnotationUsage(type: UastAnnotationUsageType): Boolean {
        return type == UastAnnotationUsageType.METHOD
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        node: UElement,
        usageType: UastAnnotationUsageType,
        annotation: UAnnotation,
        qualifiedName: String
    ) {
        val method = node.getParentOfType(UMethod::class.java) ?: return
        methodsNeedingReturnThis.add(method)
    }

    override fun visitClass(context: JavaContext, node: UClass) {
        for (method in node.methods) {
            if (methodsNeedingReturnThis.contains(method)) continue

            val psiMethod = method.sourcePsi as? PsiMethod ?: continue
            val superMethods = psiMethod.findSuperMethods()
            for (superMethod in superMethods) {
                if (context.evaluator.findAnnotation(superMethod, ANNOTATION_NAME, false) != null) {
                    methodsNeedingReturnThis.add(method)
                    break
                }
            }
        }
    }

    override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        val method = node.getParentOfType(UMethod::class.java) ?: return
        if (!methodsNeedingReturnThis.contains(method)) return

        val returnExpr = node.returnExpression
        if (returnExpr is UThisExpression) return

        val message = "Method must return `this` because it is annotated with or overrides a method annotated with `@$ANNOTATION_NAME`."
        context.report(ISSUE, node, context.getLocation(node), message)
    }
}