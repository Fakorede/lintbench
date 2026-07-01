package com.android.tools.lint.checks

import com.android.SdkConstants
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
import org.jetbrains.uast.UastFacade
import org.w3c.dom.Attr

class ViewTypeDetector : ResourceXmlDetector() {

    // Map from view id (string) to the fully qualified class name of the view
    private val idToViewTag = HashMap<String, String>()

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
                just assign the result of the `findViewById` call to variables of specific \
                view classes.

                However, due to language changes between Java 7 and 8, this change may \
                cause code to not compile without explicit casts. This lint check looks \
                for these scenarios and suggests casts to be added now such that the code \
                will continue to compile if the language level is updated to 1.8.
                """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val FIND_VIEW_BY_ID = "findViewById"
        private const val ATTR_ID = "id"
        private const val ATTR_CLASS = "class"
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(ATTR_ID)
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        // value is like "@+id/foo" or "@id/foo"
        val id = if (value.startsWith("@+id/")) {
            value.substring("@+id/".length)
        } else if (value.startsWith("@id/")) {
            value.substring("@id/".length)
        } else {
            return
        }

        // Get the tag name to determine the view type
        val element = attribute.ownerElement ?: return
        val tagName = element.tagName ?: return

        // Resolve the actual class name
        val viewClass = resolveViewClass(tagName, element)
        idToViewTag[id] = viewClass
    }

    private fun resolveViewClass(tagName: String, element: org.w3c.dom.Element): String {
        return when (tagName) {
            "view" -> {
                // <view class="com.example.MyView" />
                element.getAttribute(ATTR_CLASS).takeIf { it.isNotEmpty() } ?: tagName
            }
            "include", "merge", "fragment", "requestFocus", "tag" -> tagName
            else -> {
                // If the tag contains a dot, it's already fully qualified (or a custom view)
                tagName
            }
        }
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(FIND_VIEW_BY_ID)
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        // Check that this is a call to View.findViewById or Activity.findViewById etc.
        val containingClass = method.containingClass ?: return
        val className = containingClass.qualifiedName ?: return

        // Only handle known Android framework classes
        val knownClasses = setOf(
            "android.app.Activity",
            "android.view.View",
            "android.app.Dialog",
            "android.support.v4.app.Fragment",
            "androidx.fragment.app.Fragment",
            "android.support.v7.app.AppCompatActivity",
        )
        val isKnownClass = knownClasses.any { known ->
            className == known || isSubclassOf(context, containingClass, known)
        }
        if (!isKnownClass && !isViewOrActivitySubclass(context, containingClass)) {
            return
        }

        // Get the id argument
        val args = node.valueArguments
        if (args.isEmpty()) return
        val idArg = args[0]

        // Resolve the id to get the view type
        val idName = resolveIdName(idArg) ?: return
        val expectedViewType = idToViewTag[idName] ?: return

        // Check if there's already a cast
        val parent = skipParens(node.uastParent) ?: return

        // If already wrapped in a cast, no need to warn
        if (parent is UTypeCastExpression) return

        // Check if the parent is a qualified reference (chained call), skip in that case
        if (parent is UQualifiedReferenceExpression) return

        // The call result must be assigned or used in a context where a cast would help
        // We look for cases where the result is used without a cast
        // In Java 8, if the result is passed to an overloaded method, ambiguity can occur
        // We flag the case where the result is used as an argument to a method call
        // without an explicit cast.

        // For simplicity, report if the result is used as a method argument without a cast
        // This is the main scenario described in the issue.
        val grandParent = skipParens(parent.uastParent) ?: return

        if (isMethodArgument(node, parent, grandParent)) {
            val castType = getSimpleViewName(expectedViewType)
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Add explicit cast here; won't compile with Java 8 without it: " +
                    "`($castType) findViewById(...)`",
            )
        }
    }

    private fun isViewOrActivitySubclass(
        context: JavaContext,
        cls: com.intellij.psi.PsiClass,
    ): Boolean {
        val evaluator = context.evaluator
        return evaluator.extendsClass(cls, "android.view.View", false) ||
            evaluator.extendsClass(cls, "android.app.Activity", false) ||
            evaluator.extendsClass(cls, "android.app.Dialog", false) ||
            evaluator.extendsClass(cls, "android.support.v4.app.Fragment", false) ||
            evaluator.extendsClass(cls, "androidx.fragment.app.Fragment", false)
    }

    private fun isSubclassOf(
        context: JavaContext,
        cls: com.intellij.psi.PsiClass,
        superClass: String,
    ): Boolean {
        return context.evaluator.extendsClass(cls, superClass, false)
    }

    private fun resolveIdName(expression: UExpression): String? {
        val src = expression.asSourceString()
        // Handles R.id.foo or android.R.id.foo
        val rIdPattern = Regex("""(?:.*\.)?R\.id\.(\w+)""")
        val match = rIdPattern.find(src)
        return match?.groupValues?.get(1)
    }

    private fun skipParens(expr: org.jetbrains.uast.UElement?): org.jetbrains.uast.UElement? {
        var current = expr
        while (current is UParenthesizedExpression) {
            current = current.uastParent
        }
        return current
    }

    private fun isMethodArgument(
        node: UCallExpression,
        parent: org.jetbrains.uast.UElement,
        grandParent: org.jetbrains.uast.UElement,
    ): Boolean {
        // Check if the node is used as an argument in a method call
        if (parent is UCallExpression) {
            return parent.valueArguments.any { it === node }
        }
        if (grandParent is UCallExpression) {
            return grandParent.valueArguments.any { arg ->
                skipParens(arg) === node || arg === node
            }
        }
        return false
    }

    private fun getSimpleViewName(viewClass: String): String {
        val dot = viewClass.lastIndexOf('.')
        return if (dot >= 0) viewClass.substring(dot + 1) else viewClass
    }
}