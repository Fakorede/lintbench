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
        private val IMPLEMENTATION = Implementation(
            AppCompatCustomViewDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
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
                instead extend one of the delegate classes in \
                `androidx.appcompat.widget.AppCompatTextView`.
                """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        // Map from android.widget base class -> recommended androidx.appcompat replacement
        private val APPCOMPAT_SUPER_MAP: Map<String, String> = mapOf(
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
            "android.widget.ToggleButton" to "androidx.appcompat.widget.AppCompatToggleButton",
        )

        // Set of appcompat classes that are acceptable super classes (no warning needed)
        private val APPCOMPAT_CLASSES: Set<String> = APPCOMPAT_SUPER_MAP.values.toSet()
    }

    override fun applicableSuperClasses(): List<String> = APPCOMPAT_SUPER_MAP.keys.toList()

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Walk the super class hierarchy to find the direct android.widget superclass
        // but first check if somewhere in the hierarchy there's already an appcompat class
        val evaluator = context.evaluator

        // Check if the class itself already extends an appcompat widget somewhere in its hierarchy
        // (i.e., an intermediate class already does the right thing)
        for (appCompatClass in APPCOMPAT_CLASSES) {
            if (evaluator.extendsClass(declaration, appCompatClass, false)) {
                // Already using appcompat replacement transitively — no warning needed
                return
            }
        }

        // Find which android.widget class is being extended (directly or indirectly)
        for ((androidWidget, appCompatReplacement) in APPCOMPAT_SUPER_MAP) {
            if (evaluator.extendsClass(declaration, androidWidget, false)) {
                val superClass = declaration.javaPsi.superClass ?: break
                val superClassName = superClass.qualifiedName ?: break

                // Only warn if the *direct* superclass is the android.widget class,
                // or if an intermediate custom class directly extends it without using appcompat.
                // We report on the declaration's super class reference.
                val location = context.getNameLocation(declaration)

                context.report(
                    ISSUE,
                    declaration,
                    location,
                    "This custom view should extend `$appCompatReplacement` instead of `$superClassName`",
                )
                break
            }
        }
    }
}