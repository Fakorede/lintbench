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
import java.io.File
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UTypeCastExpression
import org.w3c.dom.Attr

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

    // Maps resource id (e.g. "my_button") -> set of view types (e.g. "android.widget.Button")
    private val idToViewTag = mutableMapOf<String, MutableSet<String>>()

    companion object {
        @JvmField
        val ISSUE = Issue.create(
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

        private const val FIND_VIEW_BY_ID = "findViewById"

        private val VIEW_TAG_TO_CLASS = mapOf(
            "View" to "android.view.View",
            "ViewGroup" to "android.view.ViewGroup",
            "TextView" to "android.widget.TextView",
            "Button" to "android.widget.Button",
            "ImageView" to "android.widget.ImageView",
            "EditText" to "android.widget.EditText",
            "CheckBox" to "android.widget.CheckBox",
            "RadioButton" to "android.widget.RadioButton",
            "ToggleButton" to "android.widget.ToggleButton",
            "Switch" to "android.widget.Switch",
            "ImageButton" to "android.widget.ImageButton",
            "ProgressBar" to "android.widget.ProgressBar",
            "SeekBar" to "android.widget.SeekBar",
            "RatingBar" to "android.widget.RatingBar",
            "Spinner" to "android.widget.Spinner",
            "ListView" to "android.widget.ListView",
            "GridView" to "android.widget.GridView",
            "ScrollView" to "android.widget.ScrollView",
            "HorizontalScrollView" to "android.widget.HorizontalScrollView",
            "LinearLayout" to "android.widget.LinearLayout",
            "RelativeLayout" to "android.widget.RelativeLayout",
            "FrameLayout" to "android.widget.FrameLayout",
            "TableLayout" to "android.widget.TableLayout",
            "TableRow" to "android.widget.TableRow",
            "WebView" to "android.webkit.WebView",
            "VideoView" to "android.widget.VideoView",
            "SurfaceView" to "android.view.SurfaceView",
            "TextureView" to "android.view.TextureView",
            "RecyclerView" to "androidx.recyclerview.widget.RecyclerView",
            "CardView" to "androidx.cardview.widget.CardView",
            "Toolbar" to "android.widget.Toolbar",
            "SearchView" to "android.widget.SearchView",
            "DatePicker" to "android.widget.DatePicker",
            "TimePicker" to "android.widget.TimePicker",
            "CalendarView" to "android.widget.CalendarView",
            "NumberPicker" to "android.widget.NumberPicker",
            "AutoCompleteTextView" to "android.widget.AutoCompleteTextView",
            "MultiAutoCompleteTextView" to "android.widget.MultiAutoCompleteTextView",
            "CheckedTextView" to "android.widget.CheckedTextView",
            "Chronometer" to "android.widget.Chronometer",
            "AnalogClock" to "android.widget.AnalogClock",
            "DigitalClock" to "android.widget.DigitalClock",
            "TextClock" to "android.widget.TextClock"
        )
    }

    // -----------------------------------------------------------------------
    // ResourceXmlDetector
    // -----------------------------------------------------------------------

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    // -----------------------------------------------------------------------
    // XmlScanner
    // -----------------------------------------------------------------------

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(ATTR_ID)
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (ANDROID_URI != attribute.namespaceURI) return

        val value = attribute.value ?: return
        // value is typically "@+id/foo" or "@id/foo"
        val id = when {
            value.startsWith("@+id/") -> value.substring(5)
            value.startsWith("@id/") -> value.substring(4)
            else -> return
        }

        val element = attribute.ownerElement ?: return
        val tagName = element.tagName ?: return

        // Resolve the tag name to a fully-qualified class name if possible
        val viewClass = resolveTagToClass(tagName)

        val set = idToViewTag.getOrPut(id) { mutableSetOf() }
        set.add(viewClass)
    }

    private fun resolveTagToClass(tagName: String): String {
        // If already fully qualified
        if (tagName.contains('.')) return tagName
        // Try our map
        return VIEW_TAG_TO_CLASS[tagName] ?: "android.view.View"
    }

    // -----------------------------------------------------------------------
    // SourceCodeScanner
    // -----------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> {
        return listOf(FIND_VIEW_BY_ID)
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Check that this is a View.findViewById or Activity.findViewById etc.
        val containingClass = method.containingClass?.qualifiedName ?: ""
        val evaluator = context.evaluator

        val isViewFindViewById = evaluator.isMemberInSubClassOf(method, "android.view.View") ||
            evaluator.isMemberInSubClassOf(method, "android.app.Activity") ||
            evaluator.isMemberInSubClassOf(method, "android.app.Dialog") ||
            evaluator.isMemberInSubClassOf(method, "android.view.Window")

        if (!isViewFindViewById) return

        // Get the argument — should be a single R.id.xxx reference
        val args = node.valueArguments
        if (args.size != 1) return

        val idArg = args[0]
        val idName = extractIdName(idArg) ?: return

        // Look up what view type this id maps to
        val viewTypes = idToViewTag[idName]
        if (viewTypes.isNullOrEmpty()) return

        // If there's only one view type and it's not ambiguous, we can suggest a cast
        if (viewTypes.size != 1) return
        val expectedType = viewTypes.first()

        // Check if the call is already wrapped in a cast
        val parent = node.uastParent
        if (parent is UTypeCastExpression) return
        if (parent is UParenthesizedExpression && parent.uastParent is UTypeCastExpression) return

        // Check the inferred type of the call expression
        val expressionType = node.getExpressionType()?.canonicalText
        // If the type is already specific (not View or Object), no cast needed
        if (expressionType != null &&
            expressionType != "android.view.View" &&
            expressionType != "java.lang.Object" &&
            expressionType != expectedType
        ) {
            // There's a type mismatch — report it
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Add explicit cast here; won't compile with Java 8 language level: " +
                    "Expected `$expectedType`, got `$expressionType`"
            )
            return
        }

        // If the result is being assigned to a typed variable, check compatibility
        val assignedType = getAssignedType(node)
        if (assignedType != null &&
            assignedType != "android.view.View" &&
            assignedType != "java.lang.Object" &&
            assignedType != expectedType &&
            !isSubtype(context, expectedType, assignedType)
        ) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Add explicit cast here; won't compile with Java 8 language level: " +
                    "Expected `$assignedType`"
            )
        }
    }

    private fun extractIdName(expression: UExpression): String? {
        // The expression source is typically "R.id.foo" — extract "foo"
        val src = expression.asSourceString()
        val dotId = src.lastIndexOf('.')
        if (dotId < 0) return null
        val candidate = src.substring(dotId + 1).trim()
        return if (candidate.isNotEmpty()) candidate else null
    }

    private fun getAssignedType(node: UCallExpression): String? {
        // Walk up the UAST tree to find a variable declaration or assignment
        var current = node.uastParent
        while (current != null) {
            if (current is org.jetbrains.uast.ULocalVariable) {
                return current.type.canonicalText
            }
            if (current is org.jetbrains.uast.UBinaryExpression) {
                val leftType = current.leftOperand.getExpressionType()?.canonicalText
                if (leftType != null) return leftType
            }
            // Stop traversal at statement boundaries
            if (current is org.jetbrains.uast.UBlockExpression ||
                current is org.jetbrains.uast.UMethod
            ) break
            current = current.uastParent
        }
        return null
    }

    private fun isSubtype(context: JavaContext, subType: String, superType: String): Boolean {
        return try {
            val evaluator = context.evaluator
            val sub = evaluator.findClass(subType) ?: return false
            val sup = evaluator.findClass(superType) ?: return false
            evaluator.extendsClass(sub, superType, false) ||
                evaluator.implementsInterface(sub, superType, false)
        } catch (e: Exception) {
            false
        }
    }
}