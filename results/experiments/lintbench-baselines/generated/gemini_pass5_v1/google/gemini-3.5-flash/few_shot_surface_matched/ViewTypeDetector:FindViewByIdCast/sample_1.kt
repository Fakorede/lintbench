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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UastBinaryExpressionWithTypeKind

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner {

    private val idToType = java.util.HashMap<String, String>()

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf("id", "android:id")
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        val value = attribute.value
        val id = valToId(value) ?: return
        val tag = attribute.ownerElement.tagName
        idToType[id] = tag
    }

    private fun valToId(value: String): String? {
        if (value.startsWith("@+id/")) {
            return value.substring(5)
        } else if (value.startsWith("@id/")) {
            return value.substring(4)
        }
        return null
    }

    override fun getApplicableMethodNames(): Collection<String> {
        return listOf("findViewById")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubClassOf(method, "android.view.View") &&
            !evaluator.isMemberInSubClassOf(method, "android.app.Activity") &&
            !evaluator.isMemberInSubClassOf(method, "android.app.Dialog") &&
            !evaluator.isMemberInSubClassOf(method, "android.view.Window")
        ) {
            return
        }

        if (node.sourcePsi?.language?.id == "Kotlin") {
            return
        }

        val args = node.valueArguments
        if (args.isEmpty()) return
        val firstArg = args[0]
        val argString = firstArg.asSourceString()
        val id = argString.substringAfterLast('.')
        if (id.isEmpty()) return

        val xmlType = idToType[id] ?: "View"

        if (hasCast(node)) {
            return
        }

        val parent = node.uastParent
        var shouldWarn = false

        if (parent is UQualifiedReferenceExpression) {
            shouldWarn = true
        } else if (parent is UCallExpression) {
            shouldWarn = true
        } else if (parent is UBinaryExpression) {
            shouldWarn = true
        } else if (parent is UIfExpression) {
            shouldWarn = true
        }

        if (shouldWarn) {
            val castExplanation = if (xmlType != "View") " (e.g. `($xmlType) findViewById(...)`)" else ""
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Add explicit cast to `$xmlType` so that the code will continue to compile if the language level is updated to 1.8$castExplanation"
            )
        }
    }

    private fun hasCast(node: UCallExpression): Boolean {
        var parent = node.uastParent
        while (parent is UQualifiedReferenceExpression) {
            parent = parent.uastParent
        }
        if (parent is UBinaryExpressionWithType) {
            val op = parent.operationKind
            if (op is UastBinaryExpressionWithTypeKind.TypeCast) {
                return true
            }
        }
        if (parent != null && parent.javaClass.simpleName.contains("TypeCast")) {
            return true
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O, the `findViewById` signature switched to using generics, which means that most of the time you can leave out explicit casts and just assign the result of the `findViewById` call to variables of specific view classes.

                However, due to language changes between Java 7 and 8, this change may cause code to not compile without explicit casts. This lint check looks for these scenarios and suggests casts to be added now such that the code will continue to compile if the language level is updated to 1.8.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                java.util.EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE)
            )
        )
    }
}