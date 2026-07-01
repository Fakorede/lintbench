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

class AppCompatCustomViewDetector : Detector(), SourceCodeScanner {

    companion object {
        private val WIDGET_TO_APPCOMPAT = mapOf(
            "android.widget.AutoCompleteTextView"
                to "androidx.appcompat.widget.AppCompatAutoCompleteTextView",
            "android.widget.Button"
                to "androidx.appcompat.widget.AppCompatButton",
            "android.widget.CheckBox"
                to "androidx.appcompat.widget.AppCompatCheckBox",
            "android.widget.CheckedTextView"
                to "androidx.appcompat.widget.AppCompatCheckedTextView",
            "android.widget.EditText"
                to "androidx.appcompat.widget.AppCompatEditText",
            "android.widget.ImageButton"
                to "androidx.appcompat.widget.AppCompatImageButton",
            "android.widget.ImageView"
                to "androidx.appcompat.widget.AppCompatImageView",
            "android.widget.MultiAutoCompleteTextView"
                to "androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView",
            "android.widget.RadioButton"
                to "androidx.appcompat.widget.AppCompatRadioButton",
            "android.widget.RatingBar"
                to "androidx.appcompat.widget.AppCompatRatingBar",
            "android.widget.SeekBar"
                to "androidx.appcompat.widget.AppCompatSeekBar",
            "android.widget.Spinner"
                to "androidx.appcompat.widget.AppCompatSpinner",
            "android.widget.TextView"
                to "androidx.appcompat.widget.AppCompatTextView",
            "android.widget.ToggleButton"
                to "androidx.appcompat.widget.AppCompatToggleButton"
        )

        private val IMPLEMENTATION = Implementation(
            AppCompatCustomViewDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AppCompatCustomView",
            briefDescription = "Appcompat Custom Widgets",
            explanation = """
                The AppCompat library automatically replaces built-in widgets such as
                Button, TextView and ImageView with AppCompat-aware versions when they
                are inflated from XML. However, this replacement does not apply to custom
                views that extend the framework widgets directly.

                To get correct behavior (including tinting, vector drawable support, etc.)
                on all API levels, custom views should extend the corresponding AppCompat
                widget from `androidx.appcompat.widget` instead of the framework
                `android.widget` class.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableSuperClasses(): List<String>? =
        WIDGET_TO_APPCOMPAT.keys.toList()

    override fun visitClass(context: JavaContext, declaration: UClass) {
        if (declaration.isInterface) {
            return
        }

        val superClassName = declaration.javaPsi.superClass?.qualifiedName ?: return
        val appCompatClassName = WIDGET_TO_APPCOMPAT[superClassName] ?: return

        if (isAppCompatClass(declaration)) {
            return
        }

        val message =
            "This custom view should extend `$appCompatClassName` instead of `$superClassName`"

        context.report(
            ISSUE,
            declaration,
            context.getNameLocation(declaration.javaPsi),
            message
        )
    }

    private fun isAppCompatClass(declaration: UClass): Boolean {
        val qualifiedName = declaration.qualifiedName ?: return false
        val packageName = qualifiedName.substringBeforeLast('.', "")
        return packageName.startsWith("androidx.appcompat.") ||
                packageName.startsWith("android.support.v7.")
    }
}