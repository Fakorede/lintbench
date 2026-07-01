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
        private val WIDGETS = listOf(
            "android.widget.AutoCompleteTextView",
            "android.widget.Button",
            "android.widget.CheckBox",
            "android.widget.CheckedTextView",
            "android.widget.EditText",
            "android.widget.ImageButton",
            "android.widget.ImageView",
            "android.widget.MultiAutoCompleteTextView",
            "android.widget.RadioButton",
            "android.widget.RatingBar",
            "android.widget.SeekBar",
            "android.widget.Spinner",
            "android.widget.TextView",
            "android.widget.ToggleButton"
        )

        private val IMPLEMENTATION = Implementation(
            AppCompatCustomViewDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AppCompatCustomView",
            briefDescription = "Appcompat Custom Widgets",
            explanation = """
                In order to support features such as tinting, the AppCompat library will
                automatically load special AppCompat replacements for the built-in widgets.
                However, this only works when your custom views extend the AppCompat versions
                of those widgets, not the `android.widget` classes directly.

                Instead of extending a class from `android.widget`, extend the corresponding
                class from `androidx.appcompat.widget` (for example, `AppCompatButton`
                instead of `Button`).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableSuperClasses(): List<String> = WIDGETS

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val superType = declaration.uastSuperTypes.firstOrNull() ?: return
        val superClass = superType.resolve() as? PsiClass ?: return
        val superName = superClass.qualifiedName ?: return

        val shortName = superName.substringAfterLast('.')
        val appCompatClass = "androidx.appcompat.widget.AppCompat$shortName"
        val message = "This custom view should extend $appCompatClass instead of $superName"

        context.report(
            ISSUE,
            declaration,
            context.getNameLocation(declaration),
            message,
        )
    }
}