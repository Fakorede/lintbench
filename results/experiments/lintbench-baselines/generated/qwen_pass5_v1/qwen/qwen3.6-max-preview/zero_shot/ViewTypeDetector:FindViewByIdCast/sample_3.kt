package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.*
import org.jetbrains.uast.*
import org.jetbrains.uast.UastUtils

class ViewTypeDetector : Detector(), UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.methodName != "findViewById") return
                if (node.valueArguments.size != 1) return
                if (node.uastParent is UCastExpression) return

                val method = node.resolve() ?: return
                val containingClass = method.containingClass ?: return
                val qualifiedName = containingClass.qualifiedName ?: return

                if (!qualifiedName.startsWith("android.") && !qualifiedName.startsWith("androidx.")) return

                val expectedType = UastUtils.getExpectedType(node) ?: return
                val expectedClass = (expectedType as? PsiClassType)?.resolve() ?: return

                val viewClass = JavaPsiFacade.getInstance(node.psi.project)
                    .findClass("android.view.View", node.psi.resolveScope) ?: return

                if (expectedClass.qualifiedName == "android.view.View") return
                if (!expectedClass.isInheritor(viewClass, true)) return

                val typeName = expectedClass.name
                val sourceText = node.sourcePsi?.text ?: return

                val fix = LintFix.create()
                    .name("Add explicit cast to $typeName")
                    .replace()
                    .text(sourceText)
                    .with("($typeName) $sourceText")
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
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "FindViewByIdCast",
            "Add Explicit Cast",
            "In Android O, the `findViewById` signature switched to using generics, which " +
                    "means that most of the time you can leave out explicit casts and just assign " +
                    "the result of the `findViewById` call to variables of specific view classes.\n\n" +
                    "However, due to language changes between Java 7 and 8, this change may cause " +
                    "code to not compile without explicit casts. This lint check looks for these " +
                    "scenarios and suggests casts to be added now such that the code will " +
                    "continue to compile if the language level is updated to 1.8.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(ViewTypeDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}