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
        return WIDGET_TO_APPCOMPAT.keys.toList()
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        if (declaration.isInterface || declaration.isEnum || declaration.isAnnotationType) return
        val qualifiedName = declaration.qualifiedName ?: return
        if (qualifiedName.startsWith("androidx.appcompat.")) return

        for (superType in declaration.uastSuperTypes) {
            val cls = context.evaluator.getTypeClass(superType.type)
            val qName = cls?.qualifiedName ?: continue
            val replacement = WIDGET_TO_APPCOMPAT[qName]
            if (replacement != null) {
                val location = context.getLocation(superType)
                val message = "Custom view should extend `$replacement` instead of `$qName` to support appcompat features like tinting"
                context.report(ISSUE, location, message)
                break
            }
        }
    }

    companion object {
        private val WIDGET_TO_APPCOMPAT = mapOf(
            "android.widget.TextView" to "androidx.appcompat.widget.AppCompatTextView",
            "android.widget.ImageView" to "androidx.appcompat.widget.AppCompatImageView",
            "android.widget.Button" to "androidx.appcompat.widget.AppCompatButton",
            "android.widget.EditText" to "androidx.appcompat.widget.AppCompatEditText",
            "android.widget.Spinner" to "androidx.appcompat.widget.AppCompatSpinner",
            "android.widget.ImageButton" to "androidx.appcompat.widget.AppCompatImageButton",
            "android.widget.CheckBox" to "androidx.appcompat.widget.AppCompatCheckBox",
            "android.widget.RadioButton" to "androidx.appcompat.widget.AppCompatRadioButton",
            "android.widget.CheckedTextView" to "androidx.appcompat.widget.AppCompatCheckedTextView",
            "android.widget.AutoCompleteTextView" to "androidx.appcompat.widget.AppCompatAutoCompleteTextView",
            "android.widget.MultiAutoCompleteTextView" to "androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView",
            "android.widget.RatingBar" to "androidx.appcompat.widget.AppCompatRatingBar",
            "android.widget.SeekBar" to "androidx.appcompat.widget.AppCompatSeekBar",
            "android.widget.Switch" to "androidx.appcompat.widget.SwitchCompat"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AppCompatCustomView",
            briefDescription = "Custom view should extend AppCompat widget",
            explanation = """
                In order to support features such as tinting, the appcompat library will \
                automatically load special appcompat replacements for the builtin widgets. \
                However, this does not work for your own custom views.

                Instead of extending the `android.widget` classes directly, you should \
                instead extend one of the delegate classes in `androidx.appcompat.widget`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppCompatCustomViewDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}