package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement

class AppCompatCustomViewDetector : Detector(), SourceCodeScanner {

    override fun applicableSuperClasses(): List<String> = WIDGETS

    override fun visitClass(context: JavaContext, declaration: UClass) {
        if (declaration.isInterface) {
            return
        }

        val qualifiedName = declaration.qualifiedName
        if (qualifiedName != null && qualifiedName.startsWith(ANDROIDX_APPCOMPAT_PACKAGE)) {
            return
        }

        for (superType in declaration.uastSuperTypes) {
            val psiType = superType.type
            if (psiType !is PsiClassType) {
                continue
            }

            val superClass = context.evaluator.getTypeClass(psiType) ?: continue
            val superClassName = context.evaluator.getQualifiedName(superClass) ?: continue
            val replacement = REPLACEMENTS[superClassName] ?: continue

            val message = "This custom view should extend $replacement instead of $superClassName to support appcompat features such as tinting."

            val referencedName = superType.sourcePsi?.text ?: superClassName
            val fix = LintFix.create()
                .replace()
                .text(referencedName)
                .with(replacement)
                .build()

            context.report(
                ISSUE,
                superType,
                context.getLocation(superType),
                message,
                fix
            )
            return
        }
    }

    companion object {
        private const val ANDROIDX_APPCOMPAT_PACKAGE = "androidx.appcompat.widget."

        private val REPLACEMENTS = mapOf(
            "android.widget.TextView" to "androidx.appcompat.widget.AppCompatTextView",
            "android.widget.EditText" to "androidx.appcompat.widget.AppCompatEditText",
            "android.widget.Button" to "androidx.appcompat.widget.AppCompatButton",
            "android.widget.ImageButton" to "androidx.appcompat.widget.AppCompatImageButton",
            "android.widget.ImageView" to "androidx.appcompat.widget.AppCompatImageView",
            "android.widget.CheckBox" to "androidx.appcompat.widget.AppCompatCheckBox",
            "android.widget.RadioButton" to "androidx.appcompat.widget.AppCompatRadioButton",
            "android.widget.CheckedTextView" to "androidx.appcompat.widget.AppCompatCheckedTextView",
            "android.widget.AutoCompleteTextView" to "androidx.appcompat.widget.AppCompatAutoCompleteTextView",
            "android.widget.MultiAutoCompleteTextView" to "androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView",
            "android.widget.Spinner" to "androidx.appcompat.widget.AppCompatSpinner",
            "android.widget.RatingBar" to "androidx.appcompat.widget.AppCompatRatingBar",
            "android.widget.SeekBar" to "androidx.appcompat.widget.AppCompatSeekBar",
            "android.widget.ProgressBar" to "androidx.appcompat.widget.AppCompatProgressBar",
            "android.widget.ToggleButton" to "androidx.appcompat.widget.AppCompatToggleButton",
            "android.widget.Switch" to "androidx.appcompat.widget.SwitchCompat"
        )

        private val WIDGETS = REPLACEMENTS.keys.toList()

        val ISSUE = Issue.create(
            id = "AppCompatCustomView",
            briefDescription = "Appcompat Custom Widgets",
            explanation = """
                In order to support features such as tinting, the appcompat library will
                automatically load special appcompat replacements for the builtin widgets.
                However, this does not work for your own custom views.

                Instead of extending the `android.widget` classes directly, you should
                instead extend one of the delegate classes in `androidx.appcompat.widget`,
                such as `AppCompatTextView`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppCompatCustomViewDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}