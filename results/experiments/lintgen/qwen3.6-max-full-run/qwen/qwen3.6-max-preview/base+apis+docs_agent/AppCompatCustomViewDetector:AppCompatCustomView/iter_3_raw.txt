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

    override fun applicableSuperClasses(): List<String>? = REPLACEMENTS.keys.toList()

    override fun visitClass(context: JavaContext, declaration: UClass) {
        if (declaration.isInterface || declaration.isEnum || declaration.isAnnotationType) return
        val qualifiedName = declaration.qualifiedName ?: return
        if (qualifiedName.startsWith("android.") || qualifiedName.startsWith("androidx.") || qualifiedName.startsWith("java.")) return

        for (superTypeRef in declaration.uastSuperTypes) {
            val superClass = context.evaluator.getTypeClass(superTypeRef.type) ?: continue
            val superFqn = superClass.qualifiedName ?: continue
            val replacement = REPLACEMENTS[superFqn] ?: continue

            val sourceText = superTypeRef.sourcePsi?.text ?: superFqn.substringAfterLast('.')
            val message = "This custom view should extend `$replacement` instead"
            val fix = fix()
                .replace()
                .text(sourceText)
                .with(replacement)
                .shortenNames()
                .build()

            context.report(ISSUE, superTypeRef, context.getLocation(superTypeRef), message, fix)
            return
        }
    }

    companion object {
        private val REPLACEMENTS = mapOf(
            "android.widget.TextView" to "androidx.appcompat.widget.AppCompatTextView",
            "android.widget.Button" to "androidx.appcompat.widget.AppCompatButton",
            "android.widget.EditText" to "androidx.appcompat.widget.AppCompatEditText",
            "android.widget.AutoCompleteTextView" to "androidx.appcompat.widget.AppCompatAutoCompleteTextView",
            "android.widget.MultiAutoCompleteTextView" to "androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView",
            "android.widget.CheckBox" to "androidx.appcompat.widget.AppCompatCheckBox",
            "android.widget.RadioButton" to "androidx.appcompat.widget.AppCompatRadioButton",
            "android.widget.CheckedTextView" to "androidx.appcompat.widget.AppCompatCheckedTextView",
            "android.widget.ToggleButton" to "androidx.appcompat.widget.AppCompatToggleButton",
            "android.widget.Switch" to "androidx.appcompat.widget.SwitchCompat",
            "android.widget.Spinner" to "androidx.appcompat.widget.AppCompatSpinner",
            "android.widget.ProgressBar" to "androidx.appcompat.widget.AppCompatProgressBar",
            "android.widget.SeekBar" to "androidx.appcompat.widget.AppCompatSeekBar",
            "android.widget.RatingBar" to "androidx.appcompat.widget.AppCompatRatingBar",
            "android.widget.ImageButton" to "androidx.appcompat.widget.AppCompatImageButton",
            "android.widget.ImageView" to "androidx.appcompat.widget.AppCompatImageView"
        )

        @JvmField
        val ISSUE = Issue.create(
            "AppCompatCustomView",
            "Appcompat Custom Widgets",
            "In order to support features such as tinting, the appcompat library will " +
            "automatically load special appcompat replacements for the builtin widgets. " +
            "However, this does not work for your own custom views.\n" +
            "\n" +
            "Instead of extending the `android.widget` classes directly, you should " +
            "instead extend one of the delegate classes in `androidx.appcompat.widget.AppCompatTextView`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(
                AppCompatCustomViewDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}