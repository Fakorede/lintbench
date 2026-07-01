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

    override fun applicableSuperClasses(): List<String>? {
        return WIDGET_MAP.keys.toList()
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val superClass = declaration.superClass ?: return
        val superClassName = superClass.qualifiedName ?: return

        if (!WIDGET_MAP.containsKey(superClassName)) {
            return
        }

        val qualifiedName = declaration.qualifiedName
        if (qualifiedName != null && (qualifiedName.startsWith("androidx.appcompat.") || qualifiedName.startsWith("android.support.v7."))) {
            return
        }

        val replacementShort = WIDGET_MAP[superClassName] ?: return
        val prefix = if (context.project.isAndroidx()) "androidx.appcompat.widget." else "android.support.v7.widget."
        val replacement = prefix + replacementShort

        val message = "This custom view should extend `$replacement` instead"

        val location = context.getNameLocation(declaration)
        context.report(
            ISSUE,
            declaration,
            location,
            message
        )
    }

    companion object {
        private val WIDGET_MAP = mapOf(
            "android.widget.TextView" to "AppCompatTextView",
            "android.widget.ImageView" to "AppCompatImageView",
            "android.widget.Button" to "AppCompatButton",
            "android.widget.EditText" to "AppCompatEditText",
            "android.widget.Spinner" to "AppCompatSpinner",
            "android.widget.ImageButton" to "AppCompatImageButton",
            "android.widget.CheckBox" to "AppCompatCheckBox",
            "android.widget.RadioButton" to "AppCompatRadioButton",
            "android.widget.CheckedTextView" to "AppCompatCheckedTextView",
            "android.widget.AutoCompleteTextView" to "AppCompatAutoCompleteTextView",
            "android.widget.MultiAutoCompleteTextView" to "AppCompatMultiAutoCompleteTextView",
            "android.widget.RatingBar" to "AppCompatRatingBar",
            "android.widget.SeekBar" to "AppCompatSeekBar",
            "android.widget.ToggleButton" to "AppCompatToggleButton"
        )

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
                """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppCompatCustomViewDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}