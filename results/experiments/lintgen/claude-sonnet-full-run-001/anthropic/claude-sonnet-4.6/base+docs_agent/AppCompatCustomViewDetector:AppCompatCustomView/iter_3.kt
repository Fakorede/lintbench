package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import org.jetbrains.uast.UClass

class AppCompatCustomViewDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "AppCompatCustomView",
            briefDescription = "Appcompat Custom Widgets",
            explanation = """
                In order to support features such as tinting, the appcompat library will \
                automatically load special appcompat replacements for the builtin widgets. \
                However, this does not work for your own custom views.

                Instead of extending the `android.widget` classes directly, you should \
                instead extend one of the delegate classes in \
                `androidx.appcompat.widget.AppCompatTextView`.
                """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppCompatCustomViewDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        // Map from android.widget class -> recommended appcompat replacement (androidx)
        private val APPCOMPAT_REPLACEMENTS: Map<String, String> = mapOf(
            "android.widget.AutoCompleteTextView" to "androidx.appcompat.widget.AppCompatAutoCompleteTextView",
            "android.widget.Button" to "androidx.appcompat.widget.AppCompatButton",
            "android.widget.CheckBox" to "androidx.appcompat.widget.AppCompatCheckBox",
            "android.widget.CheckedTextView" to "androidx.appcompat.widget.AppCompatCheckedTextView",
            "android.widget.EditText" to "androidx.appcompat.widget.AppCompatEditText",
            "android.widget.ImageButton" to "androidx.appcompat.widget.AppCompatImageButton",
            "android.widget.ImageView" to "androidx.appcompat.widget.AppCompatImageView",
            "android.widget.MultiAutoCompleteTextView" to "androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView",
            "android.widget.RadioButton" to "androidx.appcompat.widget.AppCompatRadioButton",
            "android.widget.RatingBar" to "androidx.appcompat.widget.AppCompatRatingBar",
            "android.widget.SeekBar" to "androidx.appcompat.widget.AppCompatSeekBar",
            "android.widget.Spinner" to "androidx.appcompat.widget.AppCompatSpinner",
            "android.widget.TextView" to "androidx.appcompat.widget.AppCompatTextView",
            "android.widget.ToggleButton" to "androidx.appcompat.widget.AppCompatToggleButton"
        )

        // Map from android.widget class -> recommended appcompat replacement (support library)
        private val APPCOMPAT_REPLACEMENTS_SUPPORT: Map<String, String> = mapOf(
            "android.widget.AutoCompleteTextView" to "android.support.v7.widget.AppCompatAutoCompleteTextView",
            "android.widget.Button" to "android.support.v7.widget.AppCompatButton",
            "android.widget.CheckBox" to "android.support.v7.widget.AppCompatCheckBox",
            "android.widget.CheckedTextView" to "android.support.v7.widget.AppCompatCheckedTextView",
            "android.widget.EditText" to "android.support.v7.widget.AppCompatEditText",
            "android.widget.ImageButton" to "android.support.v7.widget.AppCompatImageButton",
            "android.widget.ImageView" to "android.support.v7.widget.AppCompatImageView",
            "android.widget.MultiAutoCompleteTextView" to "android.support.v7.widget.AppCompatMultiAutoCompleteTextView",
            "android.widget.RadioButton" to "android.support.v7.widget.AppCompatRadioButton",
            "android.widget.RatingBar" to "android.support.v7.widget.AppCompatRatingBar",
            "android.widget.SeekBar" to "android.support.v7.widget.AppCompatSeekBar",
            "android.widget.Spinner" to "android.support.v7.widget.AppCompatSpinner",
            "android.widget.TextView" to "android.support.v7.widget.AppCompatTextView",
            "android.widget.ToggleButton" to "android.support.v7.widget.AppCompatToggleButton"
        )

        // Set of all appcompat replacement classes (both androidx and support)
        private val APPCOMPAT_CLASSES: Set<String> = buildSet {
            addAll(APPCOMPAT_REPLACEMENTS.values)
            addAll(APPCOMPAT_REPLACEMENTS_SUPPORT.values)
        }

        // All widget classes we want to intercept
        private val ALL_WIDGET_CLASSES: List<String> = APPCOMPAT_REPLACEMENTS.keys.toList()
    }

    override fun applicableSuperClasses(): List<String> {
        return ALL_WIDGET_CLASSES
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val superClass: PsiClass = declaration.javaPsi.superClass ?: return
        val superClassName = superClass.qualifiedName ?: return

        // Check if the super class is one of the android.widget classes we care about
        val replacement = APPCOMPAT_REPLACEMENTS[superClassName] ?: return

        // Check if the project uses appcompat
        if (!usesAppCompat(context)) {
            return
        }

        // Make sure the class itself is not already an appcompat class
        val qualifiedName = declaration.qualifiedName
        if (qualifiedName != null && APPCOMPAT_CLASSES.contains(qualifiedName)) {
            return
        }

        // Determine the best replacement based on what's available in the project
        val actualReplacement = if (context.evaluator.findClass("androidx.appcompat.widget.AppCompatTextView") != null) {
            replacement
        } else {
            APPCOMPAT_REPLACEMENTS_SUPPORT[superClassName] ?: replacement
        }

        context.report(
            ISSUE,
            declaration,
            context.getNameLocation(declaration),
            "This custom view should extend `$actualReplacement` instead"
        )
    }

    private fun usesAppCompat(context: JavaContext): Boolean {
        val evaluator = context.evaluator
        return evaluator.findClass("androidx.appcompat.widget.AppCompatTextView") != null ||
            evaluator.findClass("android.support.v7.widget.AppCompatTextView") != null
    }
}