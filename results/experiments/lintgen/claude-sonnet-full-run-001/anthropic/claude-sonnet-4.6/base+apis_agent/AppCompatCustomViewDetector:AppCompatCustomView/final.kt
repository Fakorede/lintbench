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

        private val WIDGET_TO_APPCOMPAT_SUPPORT = mapOf(
            "android.widget.AutoCompleteTextView" to "android.support.v7.widget.AppCompatAutoCompleteTextView",
            "android.widget.Button" to "android.support.v7.widget.AppCompatButton",
            "android.widget.CheckBox" to "android.support.v7.widget.AppCompatCheckBox",
            "android.widget.CheckedTextView" to "android.support.v7.widget.AppCompatCheckedTextView",
            "android.widget.EditText" to "android.support.v7.widget.AppCompatEditText",
            "android.widget.ImageButton" to "android.support.v7.widget.AppCompatImageButton",
            "android.widget.ImageView" to "android.support.v7.widget.AppCompatImageView",
            "android.widget.MultiAutoCompleteTextView" to "android.support.v7.widget.AppCompatMultiAutoCompleteTextView",
            "android.widget.RadioButton" to "android.support.v7.widget.AppCompatRadioButton",
            "android.widget.RatingBar" to "android.support.v7.widget.AppCompatRatingBar",
            "android.widget.SeekBar" to "android.support.v7.widget.AppCompatSeekBar",
            "android.widget.Spinner" to "android.support.v7.widget.AppCompatSpinner",
            "android.widget.TextView" to "android.support.v7.widget.AppCompatTextView",
            "android.widget.ToggleButton" to "android.support.v7.widget.AppCompatToggleButton"
        )

        private val APPCOMPAT_CLASSES_ANDROIDX = WIDGET_TO_APPCOMPAT.values.toSet()
        private val APPCOMPAT_CLASSES_SUPPORT = WIDGET_TO_APPCOMPAT_SUPPORT.values.toSet()
        private val ALL_APPCOMPAT_CLASSES = APPCOMPAT_CLASSES_ANDROIDX + APPCOMPAT_CLASSES_SUPPORT
    }

    override fun applicableSuperClasses(): List<String> {
        return WIDGET_TO_APPCOMPAT.keys.toList()
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val qualifiedName = declaration.qualifiedName ?: ""
        if (ALL_APPCOMPAT_CLASSES.contains(qualifiedName)) {
            return
        }

        val dependsOnAndroidX = context.project.dependsOn("androidx.appcompat:appcompat") == true
        val dependsOnSupport = context.project.dependsOn("com.android.support:appcompat-v7") == true

        if (!dependsOnAndroidX && !dependsOnSupport) {
            return
        }

        val superClass = declaration.javaPsi.superClass ?: return
        val superClassName = superClass.qualifiedName ?: return

        val appCompatReplacement = if (dependsOnAndroidX) {
            WIDGET_TO_APPCOMPAT[superClassName]
        } else {
            WIDGET_TO_APPCOMPAT_SUPPORT[superClassName]
        } ?: return

        if (isAppCompatDescendant(superClass)) {
            return
        }

        val superTypeReference = declaration.uastSuperTypes.firstOrNull { superType ->
            val resolvedClass = superType.getQualifiedName()
            resolvedClass == superClassName
        }

        val locationElement = superTypeReference ?: declaration

        context.report(
            ISSUE,
            declaration,
            context.getLocation(locationElement),
            "This custom view should extend `$appCompatReplacement` instead of `$superClassName` to allow AppCompat tinting"
        )
    }

    private fun isAppCompatDescendant(psiClass: PsiClass): Boolean {
        val qualifiedName = psiClass.qualifiedName ?: return false
        if (ALL_APPCOMPAT_CLASSES.contains(qualifiedName)) {
            return true
        }
        val superClass = psiClass.superClass ?: return false
        val superName = superClass.qualifiedName ?: return false
        if (superName == "java.lang.Object") {
            return false
        }
        return isAppCompatDescendant(superClass)
    }
}