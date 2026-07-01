package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.DOT_XML
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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UTypeCastExpression
import org.w3c.dom.Attr
import java.io.File
import java.util.EnumSet

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

    /**
     * Map from resource id (e.g. "my_button") to the set of view types
     * declared for that id across all layout XML files.
     */
    private val idToViewTag: MutableMap<String, MutableSet<String>> = HashMap()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(ATTR_ID)
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (attribute.namespaceURI != ANDROID_URI) return
        val value = attribute.value ?: return
        // value is typically "@+id/foo" or "@id/foo"
        val id = when {
            value.startsWith("@+id/") -> value.substring(5)
            value.startsWith("@id/") -> value.substring(4)
            else -> return
        }
        val element = attribute.ownerElement ?: return
        val tagName = element.tagName ?: return
        val set = idToViewTag.getOrPut(id) { mutableSetOf() }
        set.add(tagName)
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("findViewById")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Make sure it's the Android View/Activity findViewById
        val containingClass = method.containingClass?.qualifiedName ?: ""
        val isViewMethod = context.evaluator.isMemberInSubClassOf(method, "android.app.Activity") ||
                context.evaluator.isMemberInSubClassOf(method, "android.view.View") ||
                context.evaluator.isMemberInSubClassOf(method, "android.view.Window") ||
                context.evaluator.isMemberInSubClassOf(method, "android.app.Dialog") ||
                containingClass == "android.app.Activity" ||
                containingClass == "android.view.View"
        if (!isViewMethod) return

        // Check if the call is already wrapped in a cast
        val parent = skipParens(node.uastParent)
        if (parent is UTypeCastExpression) {
            // Already has an explicit cast - no need to warn
            return
        }

        // Get the id argument to look up the expected view type
        val idArg = node.valueArguments.firstOrNull() ?: return
        val idName = resolveIdName(idArg) ?: return

        val viewTypes = idToViewTag[idName] ?: return
        if (viewTypes.isEmpty()) return

        // Filter out generic/unknown types
        val concreteTypes = viewTypes.filter { it != "view" && it.isNotBlank() }
        if (concreteTypes.isEmpty()) return

        // Determine the single expected type (if unambiguous)
        val expectedType = if (concreteTypes.size == 1) {
            concreteTypes.first()
        } else {
            // Multiple different view types for same id - pick the most specific or skip
            return
        }

        // Resolve the fully qualified class name for the view tag
        val fqcn = resolveViewClass(expectedType)

        // Check the inferred/assigned type at the call site
        val expressionType = node.getExpressionType()?.canonicalText ?: "android.view.View"

        // If the expression is already typed as the specific view (generics resolved it), no warning needed
        if (expressionType == fqcn || expressionType == expectedType) return

        // Only warn if the result is used as a plain View (i.e. generics didn't resolve it)
        if (expressionType != "android.view.View" && expressionType != "java.lang.Object") return

        val location = context.getLocation(node)
        context.report(
            ISSUE,
            node,
            location,
            "Add explicit cast here; won't compile with Java 8 language level without it: " +
                    "`($expectedType) findViewById(...)`"
        )
    }

    private fun skipParens(expression: UExpression?): UExpression? {
        var expr = expression
        while (expr is UParenthesizedExpression) {
            expr = expr.expression
        }
        return expr
    }

    private fun resolveIdName(expression: UExpression): String? {
        val text = expression.asSourceString()
        // Handles R.id.foo or just foo
        val dotId = text.lastIndexOf('.')
        return if (dotId >= 0) text.substring(dotId + 1).trim() else text.trim()
    }

    private fun resolveViewClass(tag: String): String {
        // If the tag already looks like a fully qualified class name
        if (tag.contains('.')) return tag
        // Map common short tag names to fully qualified names
        return when (tag) {
            "View" -> "android.view.View"
            "ViewGroup" -> "android.view.ViewGroup"
            "ViewStub" -> "android.view.ViewStub"
            "SurfaceView" -> "android.view.SurfaceView"
            "TextureView" -> "android.view.TextureView"
            "TextView" -> "android.widget.TextView"
            "Button" -> "android.widget.Button"
            "ImageButton" -> "android.widget.ImageButton"
            "ImageView" -> "android.widget.ImageView"
            "EditText" -> "android.widget.EditText"
            "CheckBox" -> "android.widget.CheckBox"
            "RadioButton" -> "android.widget.RadioButton"
            "ToggleButton" -> "android.widget.ToggleButton"
            "Switch" -> "android.widget.Switch"
            "SeekBar" -> "android.widget.SeekBar"
            "ProgressBar" -> "android.widget.ProgressBar"
            "ListView" -> "android.widget.ListView"
            "GridView" -> "android.widget.GridView"
            "ScrollView" -> "android.widget.ScrollView"
            "HorizontalScrollView" -> "android.widget.HorizontalScrollView"
            "Spinner" -> "android.widget.Spinner"
            "WebView" -> "android.webkit.WebView"
            "LinearLayout" -> "android.widget.LinearLayout"
            "RelativeLayout" -> "android.widget.RelativeLayout"
            "FrameLayout" -> "android.widget.FrameLayout"
            "TableLayout" -> "android.widget.TableLayout"
            "TableRow" -> "android.widget.TableRow"
            "GridLayout" -> "android.widget.GridLayout"
            "ConstraintLayout" -> "androidx.constraintlayout.widget.ConstraintLayout"
            "RecyclerView" -> "androidx.recyclerview.widget.RecyclerView"
            "include" -> "android.view.View"
            "merge" -> "android.view.View"
            "fragment" -> "android.view.View"
            else -> "android.widget.$tag"
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
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
            implementation = Implementation(
                ViewTypeDetector::class.java,
                EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE),
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}