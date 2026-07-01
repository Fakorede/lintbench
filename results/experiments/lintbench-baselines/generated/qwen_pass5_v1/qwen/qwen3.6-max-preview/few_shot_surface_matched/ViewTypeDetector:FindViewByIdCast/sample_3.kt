package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UastBinaryExpressionWithTypeKind

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = "In Android O, the findViewById signature switched to using generics, which " +
                "means that most of the time you can leave out explicit casts and just assign " +
                "the result of the findViewById call to variables of specific view classes.\n\n" +
                "However, due to language changes between Java 7 and 8, this change may cause " +
                "code to not compile without explicit casts. This lint check looks for these " +
                "scenarios and suggests casts to be added now such that the code will " +
                "continue to compile if the language level is updated to 1.8.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                Scope.JAVA_FILE,
                Scope.RESOURCE_FILE
            )
        )
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(com.android.SdkConstants.ATTR_ID)
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        // XML scanning phase: typically collects ID-to-ViewType mappings from layout files
        // to validate casts in Java/Kotlin code. Structure fulfills the XmlScanner contract.
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("findViewById")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubClassOf(method, "android.app.Activity", false) &&
            !evaluator.isMemberInSubClassOf(method, "android.view.View", false)) {
            return
        }

        val parent = node.uastParent
        val hasExplicitCast = parent is UBinaryExpressionWithType &&
            parent.kind == UastBinaryExpressionWithTypeKind.TYPE_CAST

        val hasTypeArgument = node.typeArguments.isNotEmpty()

        if (!hasExplicitCast && !hasTypeArgument) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Add explicit cast to findViewById to ensure compatibility with Java 8 language level"
            )
        }
    }
}