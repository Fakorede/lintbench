package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.VIEW_TAG
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
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getParentOfType
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

        // Map from id (without @id/ prefix) to view type
        private val idToViewTag = mutableMapOf<String, String>()
    }

    // XML side: collect id -> view type mappings
    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableAttributes(): Collection<String> =
        listOf(ATTR_ID)

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (attribute.namespaceURI != ANDROID_URI) return
        val value = attribute.value ?: return
        // value is typically "@+id/foo" or "@id/foo"
        val id = when {
            value.startsWith("@+id/") -> value.removePrefix("@+id/")
            value.startsWith("@id/") -> value.removePrefix("@id/")
            else -> return
        }
        val element = attribute.ownerElement ?: return
        val tag = element.tagName ?: return
        // Use the tag name as the view type; for <view class="..."> use the class attribute
        val viewType = if (tag == VIEW_TAG) {
            element.getAttribute("class").takeIf { it.isNotEmpty() } ?: tag
        } else {
            tag
        }
        idToViewTag[id] = viewType
    }

    // Java/Kotlin side: check findViewById calls
    override fun getApplicableMethodNames(): List<String> =
        listOf(FIND_VIEW_BY_ID)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Only care about android.view.View#findViewById and Activity/Dialog variants
        val containingClass = method.containingClass?.qualifiedName ?: return
        val isViewMethod = containingClass == "android.view.View" ||
            containingClass == "android.app.Activity" ||
            containingClass == "android.app.Dialog" ||
            containingClass == "android.view.Window" ||
            context.evaluator.extendsClass(
                context.evaluator.findClass(containingClass),
                "android.view.View",
                false
            ) ||
            context.evaluator.extendsClass(
                context.evaluator.findClass(containingClass),
                "android.app.Activity",
                false
            )

        if (!isViewMethod) return

        // Get the argument (the resource id)
        val args = node.valueArguments
        if (args.isEmpty()) return
        val idArg = args[0]

        // Resolve the resource id to a name
        val idName = resolveIdName(idArg) ?: return

        // Look up the expected view type
        val expectedViewType = idToViewTag[idName] ?: return

        // Check if the result is already cast
        val parent = skipParens(node.uastParent) ?: return

        // If the parent is already a cast expression, no need to warn
        if (parent is UTypeCastExpression) return

        // If the parent is a qualified reference (method call chained), skip
        if (parent is UQualifiedReferenceExpression && parent.selector == node) return

        // Check if assigned to a variable with a specific type
        val assignedVariable = node.getParentOfType<UVariable>(strict = true)
        if (assignedVariable != null) {
            val varType = assignedVariable.type.canonicalText
            // If the variable type is View or Object, no cast needed
            if (varType == "android.view.View" || varType == "java.lang.Object") return

            // Determine the simple class name from the layout tag
            val castType = resolveFullyQualifiedViewType(expectedViewType)

            // Check if the variable type matches what we expect
            if (!varType.endsWith(castType) && !castType.endsWith(varType)) {
                // The types differ; suggest a cast
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Add explicit cast here; won't compile with Java 8 language level: " +
                        "`($castType) ${context.getLocation(node).source ?: "findViewById(...)"}`",
                )
            }
        }
    }

    /**
     * Attempt to resolve the resource id argument expression to an id name string.
     */
    private fun resolveIdName(expression: UExpression): String? {
        val sourcePsi = expression.sourcePsi ?: return null
        val text = sourcePsi.text ?: return null
        // Handles patterns like R.id.foo or just extract the last segment
        return when {
            text.contains("R.id.") -> text.substringAfterLast("R.id.").trim()
            text.contains(".id.") -> text.substringAfterLast(".id.").trim()
            else -> null
        }
    }

    /**
     * Walk up through parenthesized expressions.
     */
    private fun skipParens(node: org.jetbrains.uast.UElement?): org.jetbrains.uast.UElement? {
        var current = node
        while (current is UParenthesizedExpression) {
            current = current.uastParent
        }
        return current
    }

    /**
     * Given a tag name like "TextView" or "com.example.MyView", return the
     * fully qualified class name if it's a known widget, otherwise return as-is.
     */
    private fun resolveFullyQualifiedViewType(tag: String): String {
        if (tag.contains('.')) return tag
        // Common widget packages
        return when {
            isKnownWidget(tag) -> "android.widget.$tag"
            isKnownView(tag) -> "android.view.$tag"
            else -> tag
        }
    }

    private fun isKnownWidget(tag: String): Boolean {
        return tag in setOf(
            "TextView", "EditText", "Button", "ImageButton", "ImageView",
            "CheckBox", "RadioButton", "RadioGroup", "ToggleButton", "Switch",
            "Spinner", "ProgressBar", "SeekBar", "RatingBar", "ScrollView",
            "HorizontalScrollView", "ListView", "GridView", "ExpandableListView",
            "AutoCompleteTextView", "MultiAutoCompleteTextView", "Chronometer",
            "AnalogClock", "DigitalClock", "DatePicker", "TimePicker",
            "NumberPicker", "CalendarView", "VideoView", "MediaController",
            "TabHost", "TabWidget", "TableLayout", "TableRow",
            "LinearLayout", "RelativeLayout", "FrameLayout", "GridLayout",
            "AbsoluteLayout", "ViewFlipper", "ViewSwitcher", "TextSwitcher",
            "ImageSwitcher", "ViewAnimator", "StackView", "AdapterViewFlipper",
            "SearchView", "Space", "Toolbar"
        )
    }

    private fun isKnownView(tag: String): Boolean {
        return tag in setOf(
            "View", "ViewGroup", "SurfaceView", "TextureView", "WebView",
            "ViewStub"
        )
    }
}