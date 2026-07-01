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

        private val WIDGET_TO_APPCOMPAT = mapOf(
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

        // AppCompat classes themselves - we don't want to flag these
        private val APPCOMPAT_CLASSES = WIDGET_TO_APPCOMPAT.values.toSet()
    }

    override fun applicableSuperClasses(): List<String> {
        return WIDGET_TO_APPCOMPAT.keys.toList()
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Check if this class is itself an appcompat class (don't flag appcompat's own classes)
        val qualifiedName = declaration.qualifiedName ?: return
        if (APPCOMPAT_CLASSES.contains(qualifiedName)) {
            return
        }

        // Check if the project depends on appcompat
        if (!context.project.dependsOn("androidx.appcompat:appcompat") &&
            !context.project.dependsOn("com.android.support:appcompat-v7")
        ) {
            return
        }

        val superClass = declaration.javaPsi.superClass ?: return
        val superClassName = superClass.qualifiedName ?: return

        val appCompatReplacement = WIDGET_TO_APPCOMPAT[superClassName] ?: return

        // Make sure the super class is not already an appcompat class
        if (isAppCompatDescendant(superClass)) {
            return
        }

        val superTypeReference = declaration.uastSuperTypes.firstOrNull { superType ->
            val resolved = superType.resolve()
            resolved?.qualifiedName == superClassName
        } ?: return

        context.report(
            ISSUE,
            declaration,
            context.getLocation(superTypeReference),
            "This custom view should extend `$appCompatReplacement` instead of `$superClassName` to allow AppCompat tinting"
        )
    }

    private fun isAppCompatDescendant(psiClass: PsiClass): Boolean {
        val qualifiedName = psiClass.qualifiedName ?: return false
        if (APPCOMPAT_CLASSES.contains(qualifiedName)) {
            return true
        }
        val superClass = psiClass.superClass ?: return false
        val superName = superClass.qualifiedName ?: return false
        // Avoid infinite recursion for java.lang.Object
        if (superName == "java.lang.Object") {
            return false
        }
        return isAppCompatDescendant(superClass)
    }
}