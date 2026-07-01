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
import java.io.File
import java.util.EnumSet

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner {

    /**
     * Map from id (e.g. "my_button") to the set of view types declared for that id
     * across all layout XML files.
     */
    private val idToViewTag = HashMap<String, MutableSet<String>>()

    // -----------------------------------------------------------------------
    // XmlScanner
    // -----------------------------------------------------------------------

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT ||
            folderType == ResourceFolderType.MENU ||
            folderType == ResourceFolderType.XML ||
            folderType == ResourceFolderType.DRAWABLE ||
            folderType == ResourceFolderType.NAVIGATION
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

        val element = attribute.ownerElement ?: return
        val tag = element.tagName ?: return

        idToViewTag.getOrPut(id) { mutableSetOf() }.add(tag)
    }

    // -----------------------------------------------------------------------
    // SourceCodeScanner
    // -----------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> {
        return listOf("findViewById")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Only care about android.app.Activity#findViewById,
        // android.view.View#findViewById, or similar standard ones.
        val evaluator = context.evaluator
        val containingClass = method.containingClass?.qualifiedName ?: ""
        val isRelevant = evaluator.isMemberInSubClassOf(method, "android.app.Activity") ||
            evaluator.isMemberInSubClassOf(method, "android.view.View") ||
            evaluator.isMemberInSubClassOf(method, "android.app.Dialog") ||
            evaluator.isMemberInSubClassOf(method, "androidx.core.app.ActivityCompat") ||
            containingClass.contains("Activity") ||
            containingClass.contains("Fragment") ||
            containingClass.contains("View") ||
            containingClass.contains("Dialog")
        if (!isRelevant) return

        // Check whether the call is already wrapped in a cast.
        val parent = node.uastParent
        if (parent is UTypeCastExpression) {
            // Already cast — nothing to do.
            return
        }
        if (parent is UParenthesizedExpression) {
            val grandParent = parent.uastParent
            if (grandParent is UTypeCastExpression) {
                return
            }
        }

        // Resolve the id argument to figure out the view type.
        val args = node.valueArguments
        if (args.isEmpty()) return
        val idArg = args[0]

        val id = resolveId(idArg) ?: return
        val viewTypes = idToViewTag[id] ?: return
        if (viewTypes.isEmpty()) return

        // If there are multiple different view types for this id we can't suggest a single cast.
        val viewType = if (viewTypes.size == 1) {
            viewTypes.first()
        } else {
            // Multiple types — pick the common supertype heuristic: just warn with the first one.
            viewTypes.first()
        }

        // Determine the fully qualified class name for the view type.
        val fqn = getViewClass(viewType)

        // Check whether the result is being assigned / used in a typed context.
        // If the inferred type is already the correct view type, no cast is needed.
        val expressionType = node.getExpressionType()
        val expressionTypeName = expressionType?.canonicalText
        if (expressionTypeName != null &&
            (expressionTypeName == fqn || expressionTypeName == viewType)
        ) {
            return
        }

        // If the return type is already parameterized (generic), skip.
        // In API 26+ the method is generic so the result may already be typed correctly.
        // We only warn when the result type is raw View (or Object).
        if (expressionTypeName != null &&
            expressionTypeName != "android.view.View" &&
            expressionTypeName != "java.lang.Object"
        ) {
            return
        }

        val message = "Add explicit cast here; won't compile with Java 8 language level without it"
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

    /**
     * Try to resolve an id argument expression like `R.id.my_button` to just `my_button`.
     */
    private fun resolveId(expression: UExpression): String? {
        val text = expression.asSourceString()
        // Matches R.id.foo or similar
        val dotId = text.lastIndexOf(".id.")
        if (dotId >= 0) {
            val name = text.substring(dotId + 4).trim()
            if (name.isNotEmpty()) return name
        }
        return null
    }

    /**
     * Map a simple XML tag name to a fully-qualified Android view class name.
     */
    private fun getViewClass(tag: String): String {
        if (tag.contains('.')) {
            // Already fully qualified
            return tag
        }
        return when (tag) {
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
            "WebView" -> "android.webkit.WebView"
            "VideoView" -> "android.widget.VideoView"
            "RatingBar" -> "android.widget.RatingBar"
            "DatePicker" -> "android.widget.DatePicker"
            "TimePicker" -> "android.widget.TimePicker"
            "CalendarView" -> "android.widget.CalendarView"
            "NumberPicker" -> "android.widget.NumberPicker"
            "AutoCompleteTextView" -> "android.widget.AutoCompleteTextView"
            "MultiAutoCompleteTextView" -> "android.widget.MultiAutoCompleteTextView"
            "CheckedTextView" -> "android.widget.CheckedTextView"
            "Chronometer" -> "android.widget.Chronometer"
            "AnalogClock" -> "android.widget.AnalogClock"
            "DigitalClock" -> "android.widget.DigitalClock"
            "TextClock" -> "android.widget.TextClock"
            "Space" -> "android.widget.Space"
            "GridLayout" -> "android.widget.GridLayout"
            "Toolbar" -> "android.widget.Toolbar"
            "RecyclerView" -> "androidx.recyclerview.widget.RecyclerView"
            "CardView" -> "androidx.cardview.widget.CardView"
            "ConstraintLayout" -> "androidx.constraintlayout.widget.ConstraintLayout"
            "CoordinatorLayout" -> "androidx.coordinatorlayout.widget.CoordinatorLayout"
            "AppBarLayout" -> "com.google.android.material.appbar.AppBarLayout"
            "CollapsingToolbarLayout" -> "com.google.android.material.appbar.CollapsingToolbarLayout"
            "FloatingActionButton" -> "com.google.android.material.floatingactionbutton.FloatingActionButton"
            "TabLayout" -> "com.google.android.material.tabs.TabLayout"
            "NavigationView" -> "com.google.android.material.navigation.NavigationView"
            "DrawerLayout" -> "androidx.drawerlayout.widget.DrawerLayout"
            "ViewPager" -> "androidx.viewpager.widget.ViewPager"
            "ViewPager2" -> "androidx.viewpager2.widget.ViewPager2"
            "NestedScrollView" -> "androidx.core.widget.NestedScrollView"
            "SwipeRefreshLayout" -> "androidx.swiperefreshlayout.widget.SwipeRefreshLayout"
            else -> "android.view.View"
        }
    }

    companion object {
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
            implementation = Implementation(
                ViewTypeDetector::class.java,
                EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE),
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}