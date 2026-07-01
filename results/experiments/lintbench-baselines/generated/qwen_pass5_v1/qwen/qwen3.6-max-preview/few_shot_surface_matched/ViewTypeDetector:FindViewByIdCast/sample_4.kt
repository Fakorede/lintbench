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
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCastExpression
import org.w3c.dom.Attr

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

    private val idToViewType = mutableMapOf<String, String>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return listOf(ATTR_ID)
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value
        if (!value.startsWith("@+id/") && !value.startsWith("@id/")) return
        val idName = value.substringAfter('/')
        val tagName = attribute.ownerElement?.tagName ?: return
        idToViewType[idName] = tagName
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("findViewById")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubClassOf(method, "android.app.Activity", false) &&
            !evaluator.isMemberInSubClassOf(method, "android.view.View", false)) {
            return
        }

        val args = node.valueArguments
        if (args.size != 1) return
        val arg = args[0]

        val source = arg.asSourceString()
        if (!source.contains("R.id.")) return
        val idName = source.substringAfterLast(".")
        val expectedType = idToViewType[idName] ?: return

        if (node.uastParent is UCastExpression) return

        val location = context.getLocation(node)
        val message = "Add explicit cast to $expectedType"
        context.report(ISSUE, location, message)
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Missing explicit cast for findViewById",
            explanation = "In Android O, the findViewById signature switched to using generics, which " +
                "means that most of the time you can leave out explicit casts and just assign the result " +
                "of the findViewById call to variables of specific view classes. However, due to language " +
                "changes between Java 7 and 8, this change may cause code to not compile without explicit " +
                "casts. This lint check looks for these scenarios and suggests casts to be added now such " +
                "that the code will continue to compile if the language level is updated to 1.8.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(ViewTypeDetector::class.java, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}