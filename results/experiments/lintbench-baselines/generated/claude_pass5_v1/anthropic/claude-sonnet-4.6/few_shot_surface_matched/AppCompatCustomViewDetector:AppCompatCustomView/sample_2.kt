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
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                AppCompatCustomViewDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true
        )

        // Map from android.widget base class -> recommended androidx appcompat replacement
        private val APPCOMPAT_REPLACEMENTS = mapOf(
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

        // The set of appcompat replacements — if a class already extends one of these,
        // no warning is needed
        private val APPCOMPAT_WIDGET_CLASSES = APPCOMPAT_REPLACEMENTS.values.toSet()
    }

    override fun applicableSuperClasses(): List<String> {
        return APPCOMPAT_REPLACEMENTS.keys.toList()
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val evaluator = context.evaluator

        // Find which android.widget class this extends directly
        val superClass: PsiClass = declaration.javaPsi.superClass ?: return
        val superClassName = superClass.qualifiedName ?: return

        // Check if the direct superclass is one of the android.widget classes we care about
        val replacement = APPCOMPAT_REPLACEMENTS[superClassName] ?: return

        // If the class itself is one of the appcompat replacements, skip it
        val thisClassName = declaration.qualifiedName
        if (thisClassName != null && APPCOMPAT_WIDGET_CLASSES.contains(thisClassName)) {
            return
        }

        // Check that the project actually has appcompat as a dependency;
        // if the replacement class isn't on the classpath, don't warn
        if (evaluator.findClass(replacement) == null) {
            return
        }

        val location = context.getNameLocation(declaration)
        val message = "This custom view should extend `$replacement` instead of `$superClassName` " +
            "to allow the AppCompat widget to be substituted at runtime and support " +
            "features such as tinting"

        context.report(ISSUE, declaration, location, message)
    }
}