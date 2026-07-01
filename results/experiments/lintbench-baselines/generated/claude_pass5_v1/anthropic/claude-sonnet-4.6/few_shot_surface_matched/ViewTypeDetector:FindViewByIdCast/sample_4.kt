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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UTypeCastExpression
import org.jetbrains.uast.UastCallKind
import org.w3c.dom.Attr
import java.util.EnumSet

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner {

    /**
     * Map from resource id (e.g. "my_button") to the inferred view type
     * from the layout XML (e.g. "android.widget.Button").
     */
    private val idToViewTag = HashMap<String, String>()

    // -----------------------------------------------------------------------
    // XmlScanner implementation
    // -----------------------------------------------------------------------

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(ATTR_ID)
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (ANDROID_URI != attribute.namespaceURI) return

        val value = attribute.value ?: return
        // value is like "@+id/my_button" or "@id/my_button"
        val id = when {
            value.startsWith("@+id/") -> value.substring(5)
            value.startsWith("@id/") -> value.substring(4)
            else -> return
        }

        // The tag name is the view type, e.g. "Button", "TextView", or a fully qualified name.
        val element = attribute.ownerElement ?: return
        val tagName = element.tagName ?: return

        // Resolve to a fully-qualified class name when possible.
        val fqn = fullyQualify(tagName)
        idToViewTag[id] = fqn
    }

    // -----------------------------------------------------------------------
    // SourceCodeScanner implementation
    // -----------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> {
        return listOf("findViewById")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // We only care about android.view.View#findViewById or Activity/Dialog variants
        val containingClass = method.containingClass?.qualifiedName ?: ""
        val isViewFindById = context.evaluator.isMemberInSubClassOf(method, "android.view.View") ||
            context.evaluator.isMemberInSubClassOf(method, "android.app.Activity") ||
            context.evaluator.isMemberInSubClassOf(method, "android.app.Dialog") ||
            containingClass == "android.view.View" ||
            containingClass == "android.app.Activity" ||
            containingClass == "android.app.Dialog"

        if (!isViewFindById) return

        // Get the id argument
        val idArgument = node.valueArguments.firstOrNull() ?: return
        val idName = resolveIdName(idArgument) ?: return

        // Look up what view type this id corresponds to
        val expectedViewType = idToViewTag[idName] ?: return

        // Check if the call is already wrapped in a cast
        val parent = skipParens(node.uastParent)
        if (parent is UTypeCastExpression) {
            // Already cast — no warning needed
            return
        }

        // Check if the result is assigned to a typed variable — if there's no cast, suggest one
        // We report when there's no explicit cast, which could cause issues with Java 8
        if (parent is UQualifiedReferenceExpression) {
            // The result of findViewById is being used as a receiver — likely fine
            return
        }

        // Look at the enclosing expression to see if there's already a cast
        if (isCastExpression(node)) return

        val message = "Add explicit cast here; won't compile with Java language level 1.8 " +
            "without it: `($expectedViewType) findViewById(...)`"

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            message
        )
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun isCastExpression(node: UExpression): Boolean {
        var parent = node.uastParent
        while (parent is UParenthesizedExpression) {
            parent = parent.uastParent
        }
        return parent is UTypeCastExpression
    }

    private fun skipParens(expr: org.jetbrains.uast.UElement?): org.jetbrains.uast.UElement? {
        var e = expr
        while (e is UParenthesizedExpression) {
            e = e.uastParent
        }
        return e
    }

    private fun resolveIdName(argument: UExpression): String? {
        // The argument is typically R.id.something
        val text = argument.asSourceString()
        // Extract the last segment after the last dot
        val dot = text.lastIndexOf('.')
        return if (dot >= 0) text.substring(dot + 1) else null
    }

    /**
     * Convert a short tag name (e.g. "Button") to a fully-qualified class name.
     * Fully qualified names are returned as-is.
     */
    private fun fullyQualify(tagName: String): String {
        if (tagName.contains('.')) {
            return tagName
        }
        return when (tagName) {
            "View" -> "android.view.View"
            "ViewGroup" -> "android.view.ViewGroup"
            "ViewStub" -> "android.view.ViewStub"
            "SurfaceView" -> "android.view.SurfaceView"
            "TextureView" -> "android.view.TextureView"
            "TextView" -> "android.widget.TextView"
            "Button" -> "android.widget.Button"
            "ImageView" -> "android.widget.ImageView"
            "ImageButton" -> "android.widget.ImageButton"
            "EditText" -> "android.widget.EditText"
            "CheckBox" -> "android.widget.CheckBox"
            "RadioButton" -> "android.widget.RadioButton"
            "RadioGroup" -> "android.widget.RadioGroup"
            "ToggleButton" -> "android.widget.ToggleButton"
            "Switch" -> "android.widget.Switch"
            "SeekBar" -> "android.widget.SeekBar"
            "RatingBar" -> "android.widget.RatingBar"
            "ProgressBar" -> "android.widget.ProgressBar"
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
            "GridLayout" -> "android.widget.GridLayout"
            "WebView" -> "android.webkit.WebView"
            "AutoCompleteTextView" -> "android.widget.AutoCompleteTextView"
            "MultiAutoCompleteTextView" -> "android.widget.MultiAutoCompleteTextView"
            "Chronometer" -> "android.widget.Chronometer"
            "AnalogClock" -> "android.widget.AnalogClock"
            "DigitalClock" -> "android.widget.DigitalClock"
            "DatePicker" -> "android.widget.DatePicker"
            "TimePicker" -> "android.widget.TimePicker"
            "CalendarView" -> "android.widget.CalendarView"
            "NumberPicker" -> "android.widget.NumberPicker"
            "VideoView" -> "android.widget.VideoView"
            "MediaController" -> "android.widget.MediaController"
            "ZoomControls" -> "android.widget.ZoomControls"
            "include", "merge", "fragment", "requestFocus" -> "android.view.View"
            else -> "android.view.View"
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation =
                """
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
            implementation = Implementation(
                ViewTypeDetector::class.java,
                EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE),
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}