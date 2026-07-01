package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val RETURN_THIS_ANNOTATION = "androidx.annotation.ReturnThis"

        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods annotated with `@ReturnThis` (usually in the super method that this \
                method is overriding) should also `return this`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ReturnThisDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        /**
         * Set of methods (by qualified signature) that are annotated with @ReturnThis
         * and whose overrides should also return `this`.
         */
        private val methodsRequiringReturnThis = mutableSetOf<String>()
    }

    /**
     * Tracks methods within a class that need to be checked for returning `this`.
     * Maps UMethod to whether it has at least one return statement that is NOT `this`.
     */
    private val methodsToCheck = mutableMapOf<UMethod, MutableList<UReturnExpression>>()

    override fun applicableAnnotations(): List<String> = listOf(RETURN_THIS_ANNOTATION)

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return type == AnnotationUsageType.METHOD_OVERRIDE ||
                type == AnnotationUsageType.METHOD_CALL ||
                type == AnnotationUsageType.DEFINITION
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {
        // We care about method overrides: if a method overrides a method annotated with
        // @ReturnThis, the overriding method must also return `this`.
        if (usageInfo.type == AnnotationUsageType.METHOD_OVERRIDE) {
            val method = element as? UMethod ?: return
            checkMethodReturnsThis(context, method)
        } else if (usageInfo.type == AnnotationUsageType.DEFINITION) {
            // The annotated method itself — also check it returns `this`
            val method = element as? UMethod ?: return
            checkMethodReturnsThis(context, method)
        }
    }

    override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        // Find the enclosing method
        val method = node.getContainingUMethod() ?: return

        // Check if this method is in our tracking map
        val badReturns = methodsToCheck[method] ?: return

        // Check if this return expression returns `this`
        val returnValue = node.returnExpression
        if (returnValue !is UThisExpression) {
            badReturns.add(node)
        }
    }

    override fun visitClass(context: JavaContext, node: UClass) {
        // When we finish visiting a class, report any methods that had non-`this` returns
        // We don't do reporting here since visitClass is called at class start in some APIs.
        // Instead we rely on afterCheckFile or we check inline.
        // Actually visitClass is called when we enter the class. We need afterVisitClass.
        // Let's use a different approach: we collect and report in visitClass for the
        // methods we already processed. Since visitClass is called after the class body
        // is traversed in some contexts, let's handle it differently.
    }

    /**
     * Checks that the given method returns `this` in all return paths.
     */
    private fun checkMethodReturnsThis(context: JavaContext, method: UMethod) {
        val body = method.uastBody ?: return

        val badReturns = mutableListOf<UReturnExpression>()
        var hasAnyReturn = false

        body.accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                hasAnyReturn = true
                val returnValue = node.returnExpression
                if (returnValue !is UThisExpression) {
                    badReturns.add(node)
                }
                return super.visitReturnExpression(node)
            }
        })

        for (badReturn in badReturns) {
            val returnValue = badReturn.returnExpression
            val message = if (returnValue == null) {
                "This method should return `this` (methods annotated with `@ReturnThis` must return `this`)"
            } else {
                "This method should return `this`, not `${returnValue.asSourceString()}` " +
                        "(methods annotated with `@ReturnThis` must return `this`)"
            }
            context.report(
                ISSUE,
                badReturn,
                context.getLocation(badReturn),
                message
            )
        }

        // If there are no return statements at all and the return type is not void,
        // that might be a compile error anyway, so we skip that case.
    }
}