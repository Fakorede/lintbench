package com.android.tools.lint.checks

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
import java.util.EnumSet

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

    private val idToViewType = mutableMapOf<String, String>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf("id")
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val idValue = attribute.value
        if (!idValue.startsWith("@+id/") && !idValue.startsWith("@id/")) return
        val idName = idValue.substringAfter("/")
        val element = attribute.ownerElement
        var viewType = element.tagName
        val classAttr = element.getAttribute("class")
        if (classAttr.isNotEmpty()) {
            viewType = classAttr
        }
        idToViewType[idName] = viewType
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("findViewById")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val arg = node.valueArguments.firstOrNull() ?: return
        val idName = context.evaluator.getResourceName(arg) ?: return
        val expectedType = idToViewType[idName] ?: return

        var parent = node.uastParent
        while (parent is org.jetbrains.uast.UParenthesizedExpression) {
            parent = parent.uastParent
        }
        if (parent is UCastExpression) return
        if (node.typeArgumentCount > 0) return

        val message = "Add explicit cast to $expectedType to ensure compatibility with Java 8 language level"
        context.report(ISSUE, node, context.getLocation(node), message)
    }

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
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE)
            )
        )
    }
}