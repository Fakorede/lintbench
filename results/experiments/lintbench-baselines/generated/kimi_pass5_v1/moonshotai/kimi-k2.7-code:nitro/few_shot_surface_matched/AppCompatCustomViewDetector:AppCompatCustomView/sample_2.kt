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
        private const val ANDROID_WIDGET_PREFIX = "android.widget."
        private const val APPCOMPAT_WIDGET_PREFIX = "androidx.appcompat.widget."

        @JvmField
        val ISSUE =
            Issue.create(
                id = "AppCompatCustomView",
                briefDescription = "Appcompat Custom Widgets",
                explanation =
                    """
                        In order to support features such as tinting, the appcompat library will
                        automatically load special appcompat replacements for the builtin widgets.
                        However, this does not work for your own custom views.

                        Instead of extending the `android.widget` classes directly, you should
                        instead extend one of the delegate classes in `androidx.appcompat.widget`
                        (such as `AppCompatTextView`).
                    """.trimIndent(),
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.ERROR,
                implementation = Implementation(AppCompatCustomViewDetector::class.java, Scope.JAVA_FILE_SCOPE),
                androidSpecific = true,
            )

        private val WIDGET_TO_APPCOMPAT =
            mapOf(
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
                "android.widget.Switch" to "androidx.appcompat.widget.SwitchCompat",
                "android.widget.TextView" to "androidx.appcompat.widget.AppCompatTextView",
                "android.widget.ToggleButton" to "androidx.appcompat.widget.AppCompatToggleButton",
            )
    }

    override fun applicableSuperClasses(): List<String> = WIDGET_TO_APPCOMPAT.keys.toList()

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val qualifiedName = declaration.qualifiedName ?: return
        if (qualifiedName.startsWith(ANDROID_WIDGET_PREFIX) ||
            qualifiedName.startsWith(APPCOMPAT_WIDGET_PREFIX)
        ) {
            return
        }

        val superClass = declaration.superClass ?: return
        val superName = superClass.qualifiedName ?: return
        val replacement = WIDGET_TO_APPCOMPAT[superName] ?: return

        val message =
            "To support features such as tinting, extend $replacement instead of $superName"
        context.report(
            Incident(ISSUE, declaration, context.getNameLocation(declaration), message),
        )
    }
}