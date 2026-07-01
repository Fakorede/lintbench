package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import java.util.LinkedHashSet
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastBinaryExpressionWithTypeKind
import org.w3c.dom.Attr
import org.w3c.dom.Element

private const val ATTR_CLASS = "class"
private const val ATTR_ID = "id"
private const val METHOD_FIND_VIEW_BY_ID = "findViewById"
private const val TAG_FRAGMENT = "fragment"
private const val TAG_VIEW = "view"

class ViewTypeDetector : ResourceXmlDetector() {

    private val idToViewTypes = HashMap<String, MutableSet<String>>()

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
                In Android O, the `findViewById` signature switched to using generics, which means that most of the time you can leave out explicit casts and just assign the result of the `findViewById` call to variables of specific view classes.

                However, due to language changes between Java 7 and 8, this change may cause code to not compile without explicit casts. This lint check looks for these scenarios and suggests casts to be added now such that the code will continue to compile if the language level is updated to 1.8.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableAttributes(): Collection<String>? =
        listOf(ATTR_CLASS, ATTR_ID)

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (attribute.localName != ATTR_ID) {
            return
        }

        val id = parseIdValue(attribute.value) ?: return
        val viewClass = getViewClass(attribute.ownerElement) ?: return

        idToViewTypes.getOrPut(id) { LinkedHashSet() }.add(viewClass)
    }

    override fun getApplicableMethodNames(): List<String>? =
        listOf(METHOD_FIND_VIEW_BY_ID)

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (method.typeParameters.isEmpty()) {
            return
        }

        if (node.valueArguments.size != 1) {
            return
        }

        if (node.typeArguments.isNotEmpty()) {
            return
        }

        val idName = getResourceName(stripParentheses(node.valueArguments[0])) ?: return
        val viewClasses = idToViewTypes[idName] ?: return
        if (viewClasses.size != 1) {
            return
        }
        val viewClass = viewClasses.first()

        val parent = node.uastParent
        if (parent is UBinaryExpressionWithType &&
            parent.operationKind == UastBinaryExpressionWithTypeKind.TYPE_CAST
        ) {
            return
        }

        val source = node.asSourceString()
        val parentIsReceiver = parent is UQualifiedReferenceExpression && parent.receiver === node
        val replacement = if (parentIsReceiver) {
            "(($viewClass) $source)"
        } else {
            "($viewClass) $source"
        }

        val message = "Add explicit cast to `$viewClass` when calling `findViewById`"
        val fix = LintFix.create()
            .replace()
            .range(context.getLocation(node))
            .text(source)
            .with(replacement)
            .build()

        context.report(ISSUE, node, context.getLocation(node), message, fix)
    }

    private fun parseIdValue(value: String): String? {
        val slash = value.indexOf('/')
        return if (slash != -1 && slash < value.length - 1) {
            value.substring(slash + 1)
        } else {
            null
        }
    }

    private fun getViewClass(element: Element): String? {
        val tag = element.tagName ?: return null
        if (tag.contains('.')) {
            return tag
        }
        if (tag == TAG_VIEW) {
            val cls = element.getAttribute(ATTR_CLASS)
            return if (cls.isNotBlank()) cls else null
        }
        if (tag == TAG_FRAGMENT || tag.isEmpty() || tag[0].isLowerCase()) {
            return null
        }
        return tag
    }

    private fun getResourceName(expression: UExpression?): String? {
        return when (expression) {
            is UQualifiedReferenceExpression -> {
                val selector = expression.selector
                if (selector is USimpleNameReferenceExpression) selector.identifier else null
            }
            is USimpleNameReferenceExpression -> expression.identifier
            else -> null
        }
    }

    private fun stripParentheses(expression: UExpression?): UExpression? {
        var current = expression
        while (current is UParenthesizedExpression) {
            current = current.expression
        }
        return current
    }
}