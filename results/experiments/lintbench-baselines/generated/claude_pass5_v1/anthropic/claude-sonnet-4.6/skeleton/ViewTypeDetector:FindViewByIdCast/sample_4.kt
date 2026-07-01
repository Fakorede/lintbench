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
import org.jetbrains.uast.UastFacade
import org.w3c.dom.Attr

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
                In Android O, the `findViewById` signature switched to using generics, which \
                means that most of the time you can leave out explicit casts and just assign \
                the result of the `findViewById` call to variables of specific view classes.

                However, due to language changes between Java 7 and 8, this change may cause \
                code to not compile without explicit casts. This lint check looks for these \
                scenarios and suggests casts to be added now such that the code will \
                continue to compile if the language level is updated to 1.8.
                """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val FIND_VIEW_BY_ID = "findViewById"

        // Map from id to view type, populated while visiting XML
        private val idToViewTag = HashMap<String, String>()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(SdkConstants.ATTR_ID)
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        // Strip @+id/ or @id/ prefix
        val id = when {
            value.startsWith(SdkConstants.NEW_ID_PREFIX) ->
                value.substring(SdkConstants.NEW_ID_PREFIX.length)
            value.startsWith(SdkConstants.ID_PREFIX) ->
                value.substring(SdkConstants.ID_PREFIX.length)
            else -> return
        }
        val element = attribute.ownerElement ?: return
        val tag = element.tagName ?: return
        // Store the tag (view type) for this id
        idToViewTag[id] = tag
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(FIND_VIEW_BY_ID)
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Only care about android.view.View#findViewById and Activity#findViewById etc.
        val containingClass = method.containingClass?.qualifiedName ?: ""
        val isViewMethod = containingClass == SdkConstants.CLASS_VIEW ||
            containingClass == SdkConstants.CLASS_ACTIVITY ||
            containingClass == "android.app.Dialog" ||
            containingClass == "android.view.Window" ||
            containingClass.endsWith("Activity") ||
            containingClass.endsWith("Fragment") ||
            context.evaluator.extendsClass(
                context.evaluator.findClass(containingClass),
                SdkConstants.CLASS_VIEW,
                false
            ) ||
            context.evaluator.extendsClass(
                context.evaluator.findClass(containingClass),
                SdkConstants.CLASS_ACTIVITY,
                false
            )

        // Check if the call is already wrapped in a cast
        val parent = node.uastParent
        if (parent is UParenthesizedExpression) {
            // likely a cast expression parent
            return
        }

        // Get the id argument
        val args = node.valueArguments
        if (args.isEmpty()) return
        val idArg = args[0]

        // Try to resolve the R.id.xxx reference to get the id name
        val idName = resolveIdName(idArg) ?: return

        // Look up the view type for this id
        val viewTag = idToViewTag[idName] ?: return

        // Resolve the fully qualified class name for the view tag
        val viewClass = resolveViewClass(viewTag)

        // Check if the result is being cast already
        // Walk up the parent chain to see if there's a cast
        var uastParent = node.uastParent
        // Unwrap parentheses
        while (uastParent is UParenthesizedExpression) {
            uastParent = uastParent.uastParent
        }

        // If the parent is a type cast expression, no need to warn
        if (uastParent != null) {
            val parentSourcePsi = uastParent.sourcePsi
            if (parentSourcePsi != null) {
                val parentText = parentSourcePsi.text ?: ""
                // Simple heuristic: if the parent text contains a cast to a View subtype
                if (parentText.startsWith("(") && parentText.contains(")")) {
                    return
                }
            }
        }

        // Check if the return type of the method is generic (Object) - only relevant for
        // older compilations. Since we're checking for the scenario where adding generics
        // could break compilation, we report when there's no explicit cast.
        val returnType = method.returnType?.canonicalText ?: return

        // If the return type is already specific (not View or Object), no cast needed
        if (returnType != SdkConstants.CLASS_VIEW &&
            returnType != "android.view.View" &&
            returnType != "java.lang.Object"
        ) {
            return
        }

        // Check if the result is being assigned or used in a context that would require a cast
        // Report the issue suggesting an explicit cast
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Add explicit cast here; won't compile with Java 8 without it",
        )
    }

    private fun resolveIdName(expression: UExpression): String? {
        val sourcePsi = expression.sourcePsi ?: return null
        val text = sourcePsi.text ?: return null
        // R.id.foo -> foo
        val rIdPrefix = "R.id."
        val idx = text.lastIndexOf(rIdPrefix)
        if (idx >= 0) {
            return text.substring(idx + rIdPrefix.length).trim()
        }
        return null
    }

    private fun resolveViewClass(tag: String): String {
        return when {
            tag.contains('.') -> tag // Already fully qualified
            tag == "View" -> "android.view.View"
            tag == "ViewGroup" -> "android.view.ViewGroup"
            tag == "WebView" -> "android.webkit.WebView"
            tag == "SurfaceView" -> "android.view.SurfaceView"
            tag == "TextureView" -> "android.view.TextureView"
            else -> "android.widget.$tag"
        }
    }
}