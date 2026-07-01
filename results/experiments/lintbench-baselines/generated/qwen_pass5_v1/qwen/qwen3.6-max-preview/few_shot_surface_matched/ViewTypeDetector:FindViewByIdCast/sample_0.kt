package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_ID
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCastExpression
import org.w3c.dom.Attr
import java.util.EnumSet

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(ATTR_ID)
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // Extract ID and parent view tag for potential cross-reference analysis.
        // In a full implementation, this mapping would be stored in a shared project model
        // to validate findViewById cast targets during Java/Kotlin scanning.
        val idValue = attribute.value
        val viewType = attribute.ownerElement.tagName
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("findViewById")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubClassOf(method, "android.app.Activity") &&
            !evaluator.isMemberInSubClassOf(method, "android.view.View") &&
            !evaluator.isMemberInSubClassOf(method, "androidx.core.view.ViewCompat")) {
            return
        }

        // Skip if an explicit cast is already present
        if (node.uastParent is UCastExpression) return

        val message = "Add explicit cast to findViewById to ensure compatibility with Java 8 type inference"
        context.report(ISSUE, context.getLocation(node), message)
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = "In Android O, the findViewById signature switched to using generics, " +
                "which means that most of the time you can leave out explicit casts and just assign " +
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
                EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE)
            )
        )
    }
}