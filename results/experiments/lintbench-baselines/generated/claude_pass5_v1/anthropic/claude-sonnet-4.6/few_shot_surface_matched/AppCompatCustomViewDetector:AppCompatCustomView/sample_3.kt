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

        private val APPCOMPAT_SUPER_MAP = mapOf(
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

        private val APPCOMPAT_SUPER_CLASSES = APPCOMPAT_SUPER_MAP.values.toSet()
    }

    override fun applicableSuperClasses(): List<String> {
        return APPCOMPAT_SUPER_MAP.keys.toList()
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val superClass: PsiClass = declaration.javaPsi.superClass ?: return
        val superClassName = superClass.qualifiedName ?: return

        // If the super class is already an appcompat widget, no issue
        if (APPCOMPAT_SUPER_CLASSES.contains(superClassName)) {
            return
        }

        val appCompatReplacement = APPCOMPAT_SUPER_MAP[superClassName] ?: return

        // Check that the appcompat library is on the classpath; if not, skip
        val evaluator = context.evaluator
        if (evaluator.findClass(appCompatReplacement) == null) {
            return
        }

        val superClassReference = declaration.uastSuperTypes.firstOrNull { type ->
            val psiType = type.type
            val canonicalText = psiType.canonicalText
            canonicalText == superClassName || canonicalText.startsWith("$superClassName<")
        } ?: return

        val location = context.getLocation(superClassReference)
        val message = "This custom view should extend `$appCompatReplacement` instead"
        context.report(ISSUE, declaration, location, message)
    }
}