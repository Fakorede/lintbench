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
import org.jetbrains.uast.tryResolve

class AppCompatCustomViewDetector : Detector(), SourceCodeScanner {

    override fun applicableSuperClasses(): List<String>? {
        return WIDGET_MAP.keys.toList()
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        if (declaration.isInterface) {
            return
        }

        val qualifiedName = declaration.qualifiedName
        if (qualifiedName != null && (qualifiedName.startsWith("androidx.appcompat.") || qualifiedName.startsWith("android.support.v7."))) {
            return
        }

        val superClass = declaration.superClass ?: return
        val superClassName = superClass.qualifiedName ?: return
        val appCompatName = WIDGET_MAP[superClassName] ?: return

        val superType = declaration.uastSuperTypes.firstOrNull { typeRef ->
            val resolved = typeRef.tryResolve() as? PsiClass
            resolved?.qualifiedName == superClassName
        }

        val location = superType?.let { context.getLocation(it) } ?: context.getNameLocation(declaration)
        val sourceText = superType?.sourcePsi?.text

        val fix = if (sourceText != null) {
            fix().replace()
                .range(context.getLocation(superType))
                .text(sourceText)
                .with(appCompatName)
                .shortenNames()
                .build()
        } else {
            null
        }

        val message = "This custom view should extend `$appCompatName` instead of `$superClassName`"
        context.report(ISSUE, declaration, location, message, fix)
    }

    companion object {
        private val WIDGET_MAP = mapOf(
            "android.widget.TextView" to "androidx.appcompat.widget.AppCompatTextView",
            "android.widget.ImageView" to "androidx.appcompat.widget.AppCompatImageView",
            "android.widget.Button" to "androidx.appcompat.widget.AppCompatButton",
            "android.widget.EditText" to "androidx.appcompat.widget.AppCompatEditText",
            "android.widget.Spinner" to "androidx.appcompat.widget.AppCompatSpinner",
            "android.widget.ImageButton" to "androidx.appcompat.widget.AppCompatImageButton",
            "android.widget.CheckBox" to "androidx.appcompat.widget.AppCompatCheckBox",
            "android.widget.RadioButton" to "androidx.appcompat.widget.AppCompatRadioButton",
            "android.widget.CheckedTextView" to "androidx.appcompat.widget.AppCompatCheckedTextView",
            "android.widget.AutoCompleteTextView" to "androidx.appcompat.widget.AppCompatAutoCompleteTextView",
            "android.widget.MultiAutoCompleteTextView" to "androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView",
            "android.widget.RatingBar" to "androidx.appcompat.widget.AppCompatRatingBar",
            "android.widget.SeekBar" to "androidx.appcompat.widget.AppCompatSeekBar",
            "android.widget.ToggleButton" to "androidx.appcompat.widget.AppCompatToggleButton"
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
                instead extend one of the delegate classes in `androidx.appcompat.widget.AppCompatTextView`.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppCompatCustomViewDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}