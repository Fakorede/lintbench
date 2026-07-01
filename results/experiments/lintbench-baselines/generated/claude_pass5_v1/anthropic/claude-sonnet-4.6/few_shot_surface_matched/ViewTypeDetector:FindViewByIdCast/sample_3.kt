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
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UTypeCastExpression
import org.w3c.dom.Attr

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

    /**
     * Map from id (without @+id/ prefix) to the set of view types that use that id
     * across all layout XML files.
     */
    private val idToViewType = HashMap<String, String>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(ATTR_ID)
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        // value is typically "@+id/someName" or "@id/someName"
        val idName = when {
            value.startsWith("@+id/") -> value.substring("@+id/".length)
            value.startsWith("@id/") -> value.substring("@id/".length)
            else -> return
        }
        if (idName.isEmpty()) return

        val element = attribute.ownerElement ?: return
        val tagName = element.tagName ?: return
        // Use the simple tag name as the view type (e.g. "TextView", "LinearLayout", etc.)
        val viewType = tagName

        // If there's already a mapping for this id, keep it (first one wins);
        // if a conflict is detected we could clear it, but for simplicity keep first.
        idToViewType.putIfAbsent(idName, viewType)
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("findViewById", "requireViewById")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Check that the method is the Android View/Activity/etc. findViewById
        val containingClass = method.containingClass?.qualifiedName ?: ""
        val isRelevant = containingClass == "android.app.Activity" ||
            containingClass == "android.view.View" ||
            containingClass == "android.view.Window" ||
            containingClass == "android.app.Dialog" ||
            context.evaluator.extendsClass(
                context.evaluator.findClass(containingClass),
                "android.view.View",
                true
            ) ||
            context.evaluator.extendsClass(
                context.evaluator.findClass(containingClass),
                "android.app.Activity",
                true
            )

        // We won't restrict by class — just look at the method name and signature.
        // The method should return android.view.View (or generic T extends View).
        // Check the argument is an R.id reference
        val args = node.valueArguments
        if (args.size != 1) return

        val arg = args[0]
        val argText = arg.asSourceString()

        // Extract the id name from R.id.xxx or similar
        val idName = extractIdName(argText) ?: return

        // Look up the view type for this id
        val viewType = idToViewType[idName] ?: return

        // Now check if there's already an explicit cast on this call expression.
        // Walk up to see if it's already wrapped in a cast expression.
        val parent = node.uastParent
        if (parent is UTypeCastExpression) {
            // Already has a cast, no warning needed
            return
        }
        if (parent is UParenthesizedExpression) {
            val grandParent = parent.uastParent
            if (grandParent is UTypeCastExpression) {
                return
            }
        }

        // Check if the result is being assigned to a typed variable or used in a context
        // that would require a cast. We look at the inferred return type of the call.
        val returnType = node.getExpressionType()?.canonicalText ?: return

        // If the return type is already a specific view subtype (generics resolved), no warning needed.
        // We only warn when the return type is android.view.View (not yet specialized).
        if (returnType != "android.view.View") {
            return
        }

        // Determine the fully-qualified view type to suggest
        val suggestedType = resolveViewType(viewType)

        val message = "Add explicit cast here; will be required in Java 8: " +
            "`($suggestedType) findViewById($argText)`"

        context.report(
            ISSUE,
            node,
            context.getCallLocation(node, includeReceiver = false, includeArguments = true),
            message
        )
    }

    private fun extractIdName(argText: String): String? {
        // Handles patterns like R.id.foo, android.R.id.foo, or just foo
        val dotId = ".id."
        val idx = argText.lastIndexOf(dotId)
        if (idx >= 0) {
            val name = argText.substring(idx + dotId.length).trim()
            if (name.isNotEmpty()) return name
        }
        return null
    }

    private fun resolveViewType(tagName: String): String {
        // Map common short tag names to fully-qualified names
        return when (tagName) {
            "View" -> "android.view.View"
            "ViewGroup" -> "android.view.ViewGroup"
            "TextView" -> "android.widget.TextView"
            "EditText" -> "android.widget.EditText"
            "Button" -> "android.widget.Button"
            "ImageView" -> "android.widget.ImageView"
            "ImageButton" -> "android.widget.ImageButton"
            "CheckBox" -> "android.widget.CheckBox"
            "RadioButton" -> "android.widget.RadioButton"
            "RadioGroup" -> "android.widget.RadioGroup"
            "ToggleButton" -> "android.widget.ToggleButton"
            "Switch" -> "android.widget.Switch"
            "ProgressBar" -> "android.widget.ProgressBar"
            "SeekBar" -> "android.widget.SeekBar"
            "RatingBar" -> "android.widget.RatingBar"
            "Spinner" -> "android.widget.Spinner"
            "ListView" -> "android.widget.ListView"
            "GridView" -> "android.widget.GridView"
            "ScrollView" -> "android.widget.ScrollView"
            "HorizontalScrollView" -> "android.widget.HorizontalScrollView"
            "LinearLayout" -> "android.widget.LinearLayout"
            "RelativeLayout" -> "android.widget.RelativeLayout"
            "FrameLayout" -> "android.widget.FrameLayout"
            "TableLayout" -> "android.widget.TableLayout"
            "TableRow" -> "android.widget.TableRow"
            "WebView" -> "android.webkit.WebView"
            "VideoView" -> "android.widget.VideoView"
            "CalendarView" -> "android.widget.CalendarView"
            "DatePicker" -> "android.widget.DatePicker"
            "TimePicker" -> "android.widget.TimePicker"
            "NumberPicker" -> "android.widget.NumberPicker"
            "SearchView" -> "android.widget.SearchView"
            "TabHost" -> "android.widget.TabHost"
            "TabWidget" -> "android.widget.TabWidget"
            "TextureView" -> "android.view.TextureView"
            "SurfaceView" -> "android.view.SurfaceView"
            "include" -> "android.view.View"
            "fragment" -> "android.view.View"
            "merge" -> "android.view.View"
            else -> {
                // If the tag already looks fully qualified, use it as-is
                if (tagName.contains('.')) tagName
                else "android.view.View"
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation =
                """
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
            ),
            androidSpecific = true
        )
    }
}