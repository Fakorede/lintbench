package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class ViewTypeDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "AddExplicitCast",
            briefDescription = "Add explicit cast for findViewById",
            explanation = """
                In Android O, the `findViewById` signature switched to using generics. This means that most of the time you can leave out explicit casts and just assign the result of the `findViewById` call to variables of specific view classes.
                
                However, due to language changes between Java 7 and 8, this change may cause code to not compile without explicit casts. This lint check looks for these scenarios and suggests casts to be added now such that the code will continue to compile if the language level is updated to 1.8.
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

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("findViewById")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.name == "findViewById") {
            val receiver = node.receiver
            val argumentList = node.valueArguments

            if (argumentList.size != 1) return

            val resultType = context.evaluator.getType(node)
            val expectedViewClass = context.evaluator.getContainingClass(receiver)

            if (resultType is UastErrorType || resultType == null) {
                // If the type cannot be resolved, we can't make a suggestion
                return
            }

            val viewClass = context.evaluator.resolveClassLike(expectedViewClass)
            if (viewClass != null && !context.evaluator.isSubtype(resultType, viewClass)) {
                val message = "Add explicit cast to ${expectedViewClass.name} for findViewById"
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    message
                )
            }
        }
    }

}