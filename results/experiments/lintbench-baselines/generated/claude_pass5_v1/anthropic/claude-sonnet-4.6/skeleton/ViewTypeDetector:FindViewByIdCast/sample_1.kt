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
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ViewTypeDetector : ResourceXmlDetector() {

    // Maps android:id values (stripped of @+id/ or @id/ prefix) to the tag (view class) name
    private val idToViewTag = mutableMapOf<String, String>()

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

        private fun stripIdPrefix(id: String): String {
            return when {
                id.startsWith("@+id/") -> id.substring(5)
                id.startsWith("@id/") -> id.substring(4)
                else -> id
            }
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(ATTR_ID)
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (ANDROID_URI != attribute.namespaceURI) {
            return
        }
        val value = attribute.value ?: return
        val strippedId = stripIdPrefix(value)
        if (strippedId.isEmpty()) return

        val element: Element = attribute.ownerElement ?: return
        val tagName = element.tagName ?: return

        // Store mapping from id to view tag
        // If there's a conflict (same id used for different view types), store null to indicate ambiguity
        val existing = idToViewTag[strippedId]
        if (existing == null) {
            idToViewTag[strippedId] = tagName
        } else if (existing != tagName) {
            // Conflicting types — mark as ambiguous
            idToViewTag[strippedId] = ""
        }
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(FIND_VIEW_BY_ID)
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Check that this is a View.findViewById or Activity.findViewById etc.
        val containingClass = method.containingClass ?: return
        val className = containingClass.qualifiedName ?: ""

        // Only care about Android framework or support library findViewById
        // We check by seeing if the return type is View
        val returnType = method.returnType?.canonicalText ?: return
        if (returnType != "android.view.View" && returnType != "T") {
            // Not a standard findViewById
            return
        }

        // Get the argument (the resource id)
        val arguments = node.valueArguments
        if (arguments.size != 1) return

        val idArg = arguments[0]
        val idText = idArg.asSourceString()

        // Extract the simple id name from R.id.foo
        val idName = extractIdName(idText) ?: return

        // Look up the expected view type from our layout map
        val expectedType = idToViewTag[idName]
        if (expectedType.isNullOrEmpty()) {
            // Unknown or ambiguous
            return
        }

        // Now check the parent expression — if it's already cast, no problem
        val parent = node.uastParent

        // Unwrap parenthesized expressions
        val effectiveParent = unwrapParentheses(parent)

        if (effectiveParent is UTypeCastExpression) {
            // Already has an explicit cast — no warning needed
            return
        }

        // Check if it's used in a qualified reference (method call on result)
        // e.g. findViewById(R.id.foo).setVisibility(...) — this may need a cast
        // Check if the result is assigned to a typed variable
        // For Java code, if there's no cast and it's assigned to a specific View subtype,
        // that's where the issue lies with Java 8 generics inference changes.

        // We report when there's no explicit cast present and the view type is known
        // The key scenario: result assigned to a specific type without explicit cast
        // In Java 8, type inference changed so explicit casts may be needed

        // Check if the call result is directly assigned (via parent being a local variable
        // declaration or assignment) without a cast.
        // We flag the case where the result is used without an explicit cast.

        if (effectiveParent is UQualifiedReferenceExpression) {
            // Method chained directly — might need cast
            val resolvedType = getFullyQualifiedViewType(expectedType)
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Add explicit cast here; won't compile with Java 8 without it: `($resolvedType) findViewById(...)`",
            )
            return
        }

        // For assignments without cast, check the inferred/expected type context
        val resolvedType = getFullyQualifiedViewType(expectedType)
        // Report that an explicit cast should be added
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Add explicit cast here; won't compile with Java 8 without it: `($resolvedType) findViewById(...)`",
        )
    }

    private fun unwrapParentheses(expr: UExpression?): UExpression? {
        var current = expr
        while (current is UParenthesizedExpression) {
            current = current.expression
        }
        return current
    }

    private fun extractIdName(idText: String): String? {
        // Handle R.id.foo or android.R.id.foo
        val rIdPrefix = "R.id."
        val idx = idText.lastIndexOf(rIdPrefix)
        if (idx >= 0) {
            val name = idText.substring(idx + rIdPrefix.length).trim()
            return name.ifEmpty { null }
        }
        return null
    }

    private fun getFullyQualifiedViewType(tagName: String): String {
        // If the tag already contains a dot, it's fully qualified
        if (tagName.contains('.')) {
            return tagName
        }
        // Common built-in view tags that are in android.widget or android.view
        return when (tagName) {
            "View", "ViewGroup", "ViewStub", "SurfaceView", "TextureView" ->
                "android.view.$tagName"
            "WebView" -> "android.webkit.$tagName"
            "fragment" -> "android.app.Fragment"
            else -> "android.widget.$tagName"
        }
    }
}