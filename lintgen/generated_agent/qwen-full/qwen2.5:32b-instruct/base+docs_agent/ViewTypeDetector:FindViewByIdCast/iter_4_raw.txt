package com.android.tools.lint.checks

import com.android.SdkConstants.CLASS_VIEW
import com.android.annotations.VisibleForTesting
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class ViewTypeDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        val ADD_EXPLICIT_CAST = Issue.create(
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

    override fun getApplicableUastTypes() = listOf<Class<out UElement>>(UCallExpression::class.java)

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        if ("findViewById" == node.methodName && !isGenericCall(node)) {
            val returnType = context.evaluator.getType(node)
            val variable = (node.parent as? UVariable)?.initializer
            if (variable != null && returnType != null) {
                context.report(
                    ADD_EXPLICIT_CAST,
                    node,
                    context.getLocation(node),
                    "Add explicit cast for findViewById to ensure compatibility with Java 8",
                    fixExplicitCast(context, node, returnType)
                )
            }
        }
    }

    private fun isGenericCall(node: UCallExpression): Boolean {
        return node.valueArguments.isNotEmpty() && node.valueArguments.first().type != null
    }

    private fun fixExplicitCast(context: JavaContext, node: UCallExpression, type: UastType): LintFix {
        val variableName = (node.parent as? UVariable)?.name ?: ""
        val castType = type.canonicalText
        return LintFix.create()
            .name("Add Explicit Cast")
            .replace()
            .text(node.asSourceString())
            .with("($castType)${node.asSourceString()}")
            .build()
    }
}