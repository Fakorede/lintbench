package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler

class AppCompatCustomViewDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUElementTypes(): List<Class<out UElement>>? =
        listOf(UClass::class.java)

    override fun createUElementHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                val qualifiedName = node.javaPsi.qualifiedName ?: return
                if (qualifiedName.startsWith(APPCOMPAT_ANDROIDX_PACKAGE) ||
                    qualifiedName.startsWith(APPCOMPAT_SUPPORT_PACKAGE)
                ) {
                    return
                }

                val superClass = context.evaluator.getSuperClass(node.javaPsi) ?: return
                val superName = superClass.qualifiedName ?: return
                val appCompatClass = WIDGET_TO_APPCOMPAT[superName] ?: return

                context.report(
                    ISSUE,
                    node,
                    context.getNameLocation(node),
                    "This custom view should extend `$appCompatClass` instead of `$superName`."
                )
            }
        }
    }

    companion object {
        private const val APPCOMPAT_ANDROIDX_PACKAGE = "androidx.appcompat.widget."
        private const val APPCOMPAT_SUPPORT_PACKAGE = "android.support.v7.widget."

        private val WIDGET_TO_APPCOMPAT = mapOf(
            "android.widget.AutoCompleteTextView" to "androidx.appcompat.widget.AppCompatAutoCompleteTextView",
            "android.widget.Button" to "androidx.appcompat.widget.AppCompatButton",
            "android.widget.CheckBox" to "androidx.appcompat.widget.AppCompatCheckBox",
            "android.widget.CheckedTextView" to "androidx.appcompat.widget.AppCompatCheckedTextView",
            "android.widget.EditText" to "androidx.appcompat.widget.AppCompatEditText",
            "android.widget.ImageButton" to "androidx.appcompat.widget.AppCompatImageButton",
            "android.widget.ImageView" to "androidx.appcompat.widget.AppCompatImageView",
            "android.widget.MultiAutoCompleteTextView" to "androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView",
            "android.widget.ProgressBar" to "androidx.appcompat.widget.AppCompatProgressBar",
            "android.widget.RadioButton" to "androidx.appcompat.widget.AppCompatRadioButton",
            "android.widget.RatingBar" to "androidx.appcompat.widget.AppCompatRatingBar",
            "android.widget.SeekBar" to "androidx.appcompat.widget.AppCompatSeekBar",
            "android.widget.Spinner" to "androidx.appcompat.widget.AppCompatSpinner",
            "android.widget.Switch" to "androidx.appcompat.widget.AppCompatSwitch",
            "android.widget.TextView" to "androidx.appcompat.widget.AppCompatTextView",
            "android.widget.ToggleButton" to "androidx.appcompat.widget.AppCompatToggleButton"
        )

        @JvmField
        val ISSUE = Issue.create(
            "AppCompatCustomView",
            "Appcompat Custom Widgets",
            """
                In order to support features such as tinting, the appcompat library will
                automatically load special appcompat replacements for the builtin widgets.
                However, this does not work for your own custom views.

                Instead of extending the `android.widget` classes directly, you should
                instead extend one of the delegate classes in
                `androidx.appcompat.widget.AppCompatTextView`.
            """.trimIndent(),
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            Implementation(
                AppCompatCustomViewDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}