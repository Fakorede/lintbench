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
        private val IMPLEMENTATION = Implementation(
            AppCompatCustomViewDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

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
            "android.widget.Switch",
            "android.widget.TextView",
            "android.widget.ToggleButton",
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
                instead extend one of the delegate classes in `androidx.appcompat.widget` \
                (for example, `AppCompatTextView`).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableSuperClasses(): List<String> = WIDGETS

    override fun visitClass(context: JavaContext, declaration: UClass) {
        if (declaration.name == null) return
        if (declaration.qualifiedName?.startsWith("androidx.appcompat.widget.AppCompat") == true) return

        val superClassType = declaration.javaPsi.superClassType ?: return
        val superClass = superClassType.resolve() ?: return
        val qualifiedName = superClass.qualifiedName ?: return
        if (qualifiedName !in WIDGETS) return

        val simpleName = superClass.name ?: return
        val appCompatClass = "androidx.appcompat.widget.AppCompat$simpleName"
        val message = "This custom view should extend $appCompatClass instead of $qualifiedName"

        context.report(
            ISSUE,
            declaration,
            context.getNameLocation(declaration),
            message,
        )
    }
}