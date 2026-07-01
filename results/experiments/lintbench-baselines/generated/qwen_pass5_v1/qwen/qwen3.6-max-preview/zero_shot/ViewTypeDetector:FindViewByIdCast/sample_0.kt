package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElementHandler

class ViewTypeDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out org.jetbrains.uast.UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.methodName != "findViewById" || node.valueArgumentCount != 1) {
                    return
                }

                val evaluator = context.evaluator
                val method = node.resolve() ?: return

                val parameters = method.parameterList.parameters
                if (parameters.size != 1 || parameters[0].type.canonicalText != "int") {
                    return
                }

                val expectedType = evaluator.getExpectedType(node) ?: return
                val viewClass = evaluator.findClass("android.view.View") ?: return

                if (!evaluator.extendsClass(expectedType, viewClass, false)) {
                    val sourceText = node.sourcePsi?.text ?: node.asSourceString()
                    val fix = fix()
                        .name("Add explicit cast")
                        .replace()
                        .text(sourceText)
                        .with("(View) $sourceText")
                        .build()

                    context.report(
                        ISSUE_FIND_VIEW_BY_ID_CAST,
                        node,
                        context.getLocation(node),
                        "Add explicit cast",
                        fix
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE_FIND_VIEW_BY_ID_CAST: Issue = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O, the findViewById signature switched to using generics, which \
                means that most of the time you can leave out explicit casts and just assign \
                the result of the findViewById call to variables of specific view classes.

                However, due to language changes between Java 7 and 8, this change may cause \
                code to not compile without explicit casts. This lint check looks for these \
                scenarios and suggests casts to be added now such that the code will \
                continue to compile if the language level is updated to 1.8.
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
}