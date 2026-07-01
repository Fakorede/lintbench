package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCastExpression
import org.jetbrains.uast.getParentOfType

class ViewTypeDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O, the `findViewById` signature switched to using generics, which \
                means that most of the time you can leave out explicit casts and just assign \
                the result of the `findViewById` call to variables of specific view classes.

                However, due to language changes between Java 7 and 8, this change may cause \
                code to not compile without explicit casts. This lint check looks for these \
                scenarios and suggests casts to be added now such that the code will \
                continue to compile if the language level is updated to 1.8.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("findViewById")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!isAndroidFindViewById(method)) return

        // Skip if the call is already explicitly cast
        if (node.getParentOfType<UCastExpression>(true) != null) return

        // Skip if an explicit type witness is provided (e.g., this.<TextView>findViewById(...))
        if (node.typeArguments.isNotEmpty()) return

        val evaluator = context.evaluator
        val expectedType = evaluator.getExpectedType(node) ?: return
        val viewClass = evaluator.findClass("android.view.View") ?: return

        // Only flag if the expected type is a strict subclass of View
        if (!evaluator.extendsClass(expectedType, viewClass, true)) return

        val typeName = expectedType.canonicalText
        val fix = LintFix.create()
            .replace()
            .name("Add explicit cast to $typeName")
            .range(context.getLocation(node))
            .shortenNames()
            .text(node.asSourceString())
            .with("($typeName) ${node.asSourceString()}")
            .autoFix()
            .build()

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Add explicit cast to `$typeName` for Java 8 compatibility",
            fix
        )
    }

    private fun isAndroidFindViewById(method: PsiMethod): Boolean {
        val containingClass = method.containingClass ?: return false
        val qName = containingClass.qualifiedName ?: return false
        return qName == "android.app.Activity" ||
               qName == "android.view.View" ||
               qName == "android.app.Dialog" ||
               qName == "android.app.Fragment" ||
               qName == "androidx.fragment.app.Fragment" ||
               qName == "android.support.v4.app.Fragment"
    }
}