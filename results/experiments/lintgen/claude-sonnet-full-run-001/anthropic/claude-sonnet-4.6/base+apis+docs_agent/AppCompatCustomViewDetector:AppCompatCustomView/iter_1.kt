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

        // Map from android.widget class -> recommended androidx.appcompat.widget class
        private val ANDROIDX_REPLACEMENTS: Map<String, String> = mapOf(
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

        // Map from android.widget class -> recommended android.support.v7.widget class
        private val SUPPORT_REPLACEMENTS: Map<String, String> = mapOf(
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

        // Set of all appcompat widget class names (both old and new) to avoid flagging them
        private val APPCOMPAT_WIDGET_CLASSES: Set<String> = buildSet {
            addAll(ANDROIDX_REPLACEMENTS.values)
            addAll(SUPPORT_REPLACEMENTS.values)
        }

        // All android.widget super classes we care about
        private val ALL_WIDGET_CLASSES: List<String> = ANDROIDX_REPLACEMENTS.keys.toList()
    }

    override fun applicableSuperClasses(): List<String> {
        return ALL_WIDGET_CLASSES
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val superClass: PsiClass = declaration.javaPsi.superClass ?: return
        val superClassName = superClass.qualifiedName ?: return

        // If the super class is already an appcompat widget, no need to warn
        if (APPCOMPAT_WIDGET_CLASSES.contains(superClassName)) {
            return
        }

        // Check if the super class is one of the android.widget classes we care about
        val widgetClass = superClassName.takeIf { ANDROIDX_REPLACEMENTS.containsKey(it) } ?: return

        // Determine which appcompat library is available and pick the right replacement
        val evaluator = context.evaluator
        val replacement: String = when {
            evaluator.findClass("androidx.appcompat.widget.AppCompatTextView") != null -> {
                ANDROIDX_REPLACEMENTS[widgetClass] ?: return
            }
            evaluator.findClass("android.support.v7.widget.AppCompatTextView") != null -> {
                SUPPORT_REPLACEMENTS[widgetClass] ?: return
            }
            else -> {
                // AppCompat not available in this project, skip
                return
            }
        }

        val message = "This custom view should extend `$replacement` instead"

        context.report(
            ISSUE,
            declaration,
            context.getNameLocation(declaration),
            message
        )
    }
}