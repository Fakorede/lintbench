package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCastExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Attr
import com.android.ide.common.xml.XmlFile
import com.android.resources.ResourceFolderType

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner {

    private val idToViewType = mutableMapOf<String, String>()

    override fun appliesTo(context: Context, file: XmlFile): Boolean {
        return context.resourceFolderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf("android:id")
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value
        if (!value.startsWith("@+id/") && !value.startsWith("@id/")) return
        val idName = value.substringAfter("/")
        val tag = attribute.ownerElement.tagName
        idToViewType[idName] = tag
    }

    override fun getApplicableMethodNames(): Collection<String> {
        return listOf("findViewById")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val arg = node.valueArguments.firstOrNull() ?: return
        val ref = arg as? UReferenceExpression ?: return
        val resolved = ref.resolve() as? PsiField ?: return
        val containingClass = resolved.containingClass?.qualifiedName ?: return
        if (!containingClass.endsWith(".R.id")) return

        val idName = resolved.name
        val expectedType = idToViewType[idName] ?: return

        if (node.uastParent is UCastExpression) return

        val location = context.getLocation(node)
        val message = "Add explicit cast to `$expectedType` for `findViewById` to ensure compatibility when upgrading to Java 8."
        context.report(ISSUE, node, location, message)
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Missing explicit cast for findViewById",
            explanation = "In Android O, the findViewById signature switched to using generics, which means that most of the time you can leave out explicit casts and just assign the result of the findViewById call to variables of specific view classes. However, due to language changes between Java 7 and 8, this change may cause code to not compile without explicit casts. This lint check looks for these scenarios and suggests casts to be added now such that the code will continue to compile if the language level is updated to 1.8.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(ViewTypeDetector::class.java, Scope.JAVA_AND_RESOURCE_FILES)
        )
    }
}