package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UVariable
import org.w3c.dom.Node

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

        val parent = node.uastParent ?: return

        // Skip if already explicitly cast
        if (parent.javaClass.simpleName == "UCastExpression") return

        // Skip if explicit type witness is provided (e.g., this.<TextView>findViewById(...))
        if (node.typeArguments.isNotEmpty()) return

        val expectedType: PsiType? = when (parent) {
            is UVariable -> parent.type
            is UBinaryExpression -> parent.leftOperand.getExpressionType()
            else -> null
        }

        val expectedClass = (expectedType as? PsiClassType)?.resolve() ?: return
        val evaluator = context.evaluator

        // Only flag if the expected type is a strict subclass of View
        if (!evaluator.extendsClass(expectedClass, "android.view.View", true)) return
        if (expectedClass.qualifiedName == "android.view.View") return

        val typeName = expectedType.canonicalText
        val source = node.asSourceString()
        val fix = LintFix.create()
            .replace()
            .name("Add explicit cast to $typeName")
            .text(source)
            .with("($typeName) $source")
            .shortenNames()
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