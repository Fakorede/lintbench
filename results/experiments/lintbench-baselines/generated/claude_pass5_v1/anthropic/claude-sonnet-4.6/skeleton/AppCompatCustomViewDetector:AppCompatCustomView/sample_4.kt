package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
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

        // Map from android.widget base class to its appcompat replacement
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
    }

    override fun applicableSuperClasses(): List<String> = APPCOMPAT_SUPER_MAP.keys.toList()

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Find the direct super class name
        val superClass = declaration.javaPsi.superClass ?: return
        val superClassName = superClass.qualifiedName ?: return

        val appCompatReplacement = APPCOMPAT_SUPER_MAP[superClassName] ?: return

        // Check if appcompat is on the classpath; if not, skip the warning
        val evaluator = context.evaluator
        val appCompatClass = evaluator.findClass(appCompatReplacement)
        if (appCompatClass == null) {
            // AppCompat library not available, don't flag this
            return
        }

        // Check that the class itself is not already an appcompat class
        val qualifiedName = declaration.qualifiedName ?: ""
        if (qualifiedName.startsWith("androidx.appcompat.") ||
            qualifiedName.startsWith("android.support.v7.")) {
            return
        }

        val location = context.getNameLocation(declaration)
        context.report(
            ISSUE,
            declaration,
            location,
            "This custom view should extend `$appCompatReplacement` instead of `$superClassName` " +
                "to support tinting and other appcompat features on older versions of Android",
        )
    }
}