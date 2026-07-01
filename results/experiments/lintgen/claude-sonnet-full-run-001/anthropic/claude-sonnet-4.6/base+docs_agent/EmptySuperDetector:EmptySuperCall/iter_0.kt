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
import org.jetbrains.uast.getParentOfType

class EmptySuperDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            EmptySuperDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                For methods annotated with `@EmptySuper`, overriding methods should not \
                also call the super implementation, either because it is empty, or perhaps \
                it contains code not intended to be run when the method is overridden.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        private const val EMPTY_SUPER_ANNOTATION = "androidx.annotation.EmptySuper"
    }

    override fun getApplicableMethodNames(): List<String> = listOf("super")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Check if the called method (the super method) is annotated with @EmptySuper
        if (!context.evaluator.getAllAnnotations(method, inHierarchy = true)
                .any { it.qualifiedName == EMPTY_SUPER_ANNOTATION }
        ) {
            return
        }

        // Make sure this call is actually a super call (receiver is "super")
        val receiver = node.receiver
        if (receiver == null || receiver.asSourceString() != "super") {
            return
        }

        // Find the enclosing method to provide better context in the error message
        val containingMethod = node.getParentOfType<UMethod>(strict = true)
        val methodName = containingMethod?.name ?: method.name

        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getCallLocation(node, includeReceiver = true, includeArguments = false),
            message = "No need to call `super.${methodName}()`; the super method is annotated with " +
                    "`@EmptySuper`, meaning the super implementation is empty or not intended to be called"
        )
    }
}