package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UClass

class AppCompatCustomViewDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AppCompatCustomView",
            briefDescription = "Appcompat Custom Widgets",
            explanation = """
                In order to support features such as tinting, the appcompat library will \
                automatically load special appcompat replacements for the builtin widgets. \
                However, this does not work for your own custom views.

                Instead of extending the `android.widget` classes directly, you should \
                instead extend one of the delegate classes in `androidx.appcompat.widget.AppCompatTextView`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = Implementation(
                AppCompatCustomViewDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun applicableSuperClasses(): List<String> {
        return listOf(
            "android.widget.TextView",
            "android.widget.ImageView",
            "android.widget.Button",
            "android.widget.EditText",
            "android.widget.Spinner",
            "android.widget.ImageButton",
            "android.widget.CheckBox",
            "android.widget.RadioButton",
            "android.widget.CheckedTextView",
            "android.widget.AutoCompleteTextView",
            "android.widget.MultiAutoCompleteTextView",
            "android.widget.RatingBar",
            "android.widget.SeekBar",
            "android.widget.ToggleButton"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val superClass = declaration.superClass ?: return
        val superClassName = superClass.qualifiedName ?: return

        val replacement = getAppCompatReplacement(superClassName) ?: return

        val message = "This custom view should extend $replacement instead of $superClassName"
        context.report(
            Incident(ISSUE, declaration, context.getNameLocation(declaration), message)
        )
    }

    private fun getAppCompatReplacement(className: String): String? {
        return when (className) {
            "android.widget.TextView" -> "androidx.appcompat.widget.AppCompatTextView"
            "android.widget.ImageView" -> "androidx.appcompat.widget.AppCompatImageView"
            "android.widget.Button" -> "androidx.appcompat.widget.AppCompatButton"
            "android.widget.EditText" -> "androidx.appcompat.widget.AppCompatEditText"
            "android.widget.Spinner" -> "androidx.appcompat.widget.AppCompatSpinner"
            "android.widget.ImageButton" -> "androidx.appcompat.widget.AppCompatImageButton"
            "android.widget.CheckBox" -> "androidx.appcompat.widget.AppCompatCheckBox"
            "android.widget.RadioButton" -> "androidx.appcompat.widget.AppCompatRadioButton"
            "android.widget.CheckedTextView" -> "androidx.appcompat.widget.AppCompatCheckedTextView"
            "android.widget.AutoCompleteTextView" -> "androidx.appcompat.widget.AppCompatAutoCompleteTextView"
            "android.widget.MultiAutoCompleteTextView" -> "androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView"
            "android.widget.RatingBar" -> "androidx.appcompat.widget.AppCompatRatingBar"
            "android.widget.SeekBar" -> "androidx.appcompat.widget.AppCompatSeekBar"
            "android.widget.ToggleButton" -> "androidx.appcompat.widget.AppCompatToggleButton"
            else -> null
        }
    }
}