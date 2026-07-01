package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable
import java.util.EnumSet
import org.jetbrains.uast.UAssignmentExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UTypeCastExpression
import org.jetbrains.uast.UVariable
import org.w3c.dom.Attr

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ViewTypeDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O, the findViewById signature switched to using generics, which means that most of the time
                you can leave out explicit casts and just assign the result of the findViewById call to variables of
                specific view classes.

                However, due to language changes between Java 7 and 8, this change may cause code to not compile
                without explicit casts. This lint check looks for these scenarios and suggests casts to be added now
                such that the code will continue to compile if the language level is updated to 1.8.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    private val idToViewType = mutableMapOf<String, String>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableAttributes(): Collection<String>? = listOf("id")

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (attribute.localName != "id") return
        if (attribute.namespaceURI != ANDROID_URI) return

        val value = attribute.value ?: return
        if (!value.startsWith("@+id/") && !value.startsWith("@id/")) return

        val idName = value.substringAfterLast('/', "").takeIf { it.isNotEmpty() } ?: return
        val element = attribute.ownerElement ?: return
        val tag = element.tagName

        val viewClass = when {
            tag.contains('.') -> tag
            tag.equals("View", ignoreCase = true) -> "android.view.View"
            tag.equals("view", ignoreCase = true) -> {
                element.getAttributeNS(ANDROID_URI, "class")
                    .takeIf { it.isNotBlank() } ?: return
            }
            else -> "android.widget.$tag"
        }

        idToViewType[idName] = viewClass
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("findViewById")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (method.name != "findViewById") return

        val argument = node.valueArguments.firstOrNull() ?: return
        val idName = getResourceIdName(argument) ?: return
        val expectedType = idToViewType[idName] ?: return

        val castType = getCastType(node)
        if (castType != null) {
            if (isCompatibleCast(castType, expectedType, context)) return
        } else {
            var parent = node.uastParent
            while (parent is UParenthesizedExpression) {
                parent = parent.uastParent
            }

            if (parent is UQualifiedReferenceExpression && parent.receiver == node) return

            val targetType = getTargetType(node)
            if (targetType != null && isAssignableToExpected(targetType, expectedType, context)) return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Add explicit cast to $expectedType for this findViewById call",
        )
    }

    private fun getResourceIdName(expression: UExpression): String? {
        return when (expression) {
            is UQualifiedReferenceExpression -> {
                val selector = expression.selector
                if (selector is USimpleNameReferenceExpression) selector.identifier else null
            }
            is USimpleNameReferenceExpression -> expression.identifier
            else -> expression.asSourceString().substringAfterLast('.', "")
        }?.takeIf { it.isNotEmpty() }
    }

    private fun getCastType(node: UCallExpression): PsiType? {
        var parent: UElement? = node.uastParent
        while (parent is UParenthesizedExpression) {
            parent = parent.uastParent
        }
        return (parent as? UTypeCastExpression)?.type
    }

    private fun getTargetType(node: UCallExpression): PsiType? {
        var parent: UElement? = node.uastParent
        while (parent is UParenthesizedExpression) {
            parent = parent.uastParent
        }
        return when (parent) {
            is UVariable -> parent.type
            is UAssignmentExpression -> {
                val left = parent.leftOperand
                (left as? UResolvable)?.resolve()?.let { resolved ->
                    (resolved as? PsiVariable)?.type
                }
            }
            else -> null
        }
    }

    private fun isCompatibleCast(
        castType: PsiType, expectedType: String, context: JavaContext,
    ): Boolean {
        if (castType.canonicalText == expectedType) return true
        val castClass = (castType as? PsiClassType)?.resolve() ?: return false
        val expectedClass = context.evaluator.findClass(expectedType) ?: return false
        return context.evaluator.extends(expectedClass, castClass, false)
    }

    private fun isAssignableToExpected(
        targetType: PsiType, expectedType: String, context: JavaContext,
    ): Boolean {
        val targetClass = (targetType as? PsiClassType)?.resolve() ?: return false
        val expectedClass = context.evaluator.findClass(expectedType) ?: return false
        return context.evaluator.extends(expectedClass, targetClass, false)
    }
}