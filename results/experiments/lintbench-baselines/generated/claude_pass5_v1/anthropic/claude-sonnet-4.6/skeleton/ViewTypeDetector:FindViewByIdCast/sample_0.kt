package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_TAG
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UTypeCastExpression
import org.jetbrains.uast.getParentOfType
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ViewTypeDetector : ResourceXmlDetector() {

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
                In Android O, the `findViewById` signature switched to using generics, \
                which means that most of the time you can leave out explicit casts and \
                just assign the result of the `findViewById` call to variables of \
                specific view classes.

                However, due to language changes between Java 7 and 8, this change may \
                cause code to not compile without explicit casts. This lint check looks \
                for these scenarios and suggests casts to be added now such that the \
                code will continue to compile if the language level is updated to 1.8.
                """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val FIND_VIEW_BY_ID = "findViewById"
        private const val REQUIRE_VIEW_BY_ID = "requireViewById"

        /** Map from id (without @id/ prefix) to view type tag name */
        private val idToViewTag = HashMap<String, String>()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(ATTR_ID)
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        // value is typically "@+id/foo" or "@id/foo"
        val id = when {
            value.startsWith("@+id/") -> value.substring(5)
            value.startsWith("@id/") -> value.substring(4)
            else -> return
        }
        val element: Element = attribute.ownerElement ?: return
        val tagName = element.tagName ?: return
        idToViewTag[id] = tagName
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(FIND_VIEW_BY_ID, REQUIRE_VIEW_BY_ID)
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Only care about calls that are already wrapped in a cast expression.
        // We want to flag cases where the cast is present but may be ambiguous
        // under Java 8 generics inference, i.e., the result is cast to a View subtype.

        // Check that this is a call to View.findViewById or Activity.findViewById etc.
        val containingClass = method.containingClass ?: return
        val className = containingClass.qualifiedName ?: ""

        // We accept any class that has a findViewById method (View, Activity, Dialog, etc.)
        // The important thing is the method name matches.

        // Get the argument: should be a single resource id argument
        val args = node.valueArguments
        if (args.size < 1) return

        // Walk up the parent chain to see if there's an enclosing cast expression
        val parent = skipParens(node.uastParent) ?: return

        val castExpression = when (parent) {
            is UTypeCastExpression -> parent
            is UQualifiedReferenceExpression -> {
                // Could be ((TextView) findViewById(R.id.foo)).getText()
                val grandParent = skipParens(parent.uastParent) ?: return
                if (grandParent is UTypeCastExpression) grandParent else return
            }
            else -> return
        }

        // The cast type
        val castType = castExpression.type
        val castTypeName = castType.canonicalText

        // Determine the view type from the layout resource map
        val idArg = args[0]
        val idName = resolveIdName(idArg) ?: return
        val expectedViewType = idToViewTag[idName] ?: return

        // Simplify tag name: if it contains a dot it's a fully qualified class name
        val simpleExpectedType = if (expectedViewType.contains('.')) {
            expectedViewType.substringAfterLast('.')
        } else {
            expectedViewType
        }

        // If the cast type doesn't match what we know the view to be, report
        val simpleCastType = castTypeName.substringAfterLast('.')

        if (simpleExpectedType != simpleCastType &&
            !castTypeName.endsWith(expectedViewType) &&
            expectedViewType != "View"
        ) {
            context.report(
                ISSUE,
                node,
                context.getLocation(castExpression),
                "Suspicious cast to `$simpleCastType` for a `$simpleExpectedType` with id " +
                    "`$idName`: layout tag was `$expectedViewType`",
            )
        }
    }

    private fun skipParens(expression: UExpression?): UExpression? {
        var current = expression
        while (current is UParenthesizedExpression) {
            current = current.expression
        }
        return current
    }

    private fun resolveIdName(expression: UExpression): String? {
        // The expression is typically R.id.foo — we want "foo"
        val text = expression.asSourceString()
        // Handle R.id.foo or android.R.id.foo
        val idPrefix = "R.id."
        val idx = text.lastIndexOf(idPrefix)
        if (idx >= 0) {
            return text.substring(idx + idPrefix.length)
        }
        return null
    }
}