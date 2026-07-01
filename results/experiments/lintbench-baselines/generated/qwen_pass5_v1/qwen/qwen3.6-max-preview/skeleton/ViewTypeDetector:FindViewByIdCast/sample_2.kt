package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCastExpression
import org.w3c.dom.Attr

class ViewTypeDetector : ResourceXmlDetector() {

    private val idToType = mutableMapOf<String, String>()

    companion object {
        private val IMPLEMENTATION = Implementation(
            ViewTypeDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = "In Android O, the findViewById signature switched to using generics, which " +
                "means that most of the time you can leave out explicit casts and just assign the result " +
                "of the findViewById call to variables of specific view classes. However, due to language " +
                "changes between Java 7 and 8, this change may cause code to not compile without explicit " +
                "casts. This lint check looks for these scenarios and suggests casts to be added now such " +
                "that the code will continue to compile if the language level is updated to 1.8.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun beforeCheckProject(context: Context) {
        idToType.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableAttributes(): Collection<String>? =
        listOf("android:id")

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value
        val prefix = when {
            value.startsWith("@+id/") -> "@+id/"
            value.startsWith("@id/") -> "@id/"
            else -> return
        }
        val idName = value.substring(prefix.length)
        val element = attribute.ownerElement
        var viewClass = element.tagName
        if (viewClass == "view") {
            viewClass = element.getAttribute("class")
        }
        if (viewClass.isNotEmpty()) {
            idToType[idName] = viewClass
        }
    }

    override fun getApplicableMethodNames(): List<String>? =
        listOf("findViewById")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (method.parameterList.parametersCount != 1) return
        val arg = node.valueArguments.firstOrNull() ?: return
        val resourceName = context.getResourceName(arg) ?: return
        val idName = resourceName.substringAfter("/")
        val expectedType = idToType[idName] ?: return

        // If the call is already explicitly cast, skip reporting
        if (node.uastParent is UCastExpression) return

        val location = context.getLocation(node)
        val fix = fix()
            .replace()
            .text(node.asSourceString())
            .with("($expectedType) ${node.asSourceString()}")
            .build()

        context.report(
            ISSUE, node, location,
            "Add explicit cast to `$expectedType` for Java 8 compatibility",
            fix
        )
    }
}