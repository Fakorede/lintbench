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
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private val DISALLOWED_CLASSES = setOf(
            "android.widget.TextView",
            "android.widget.ImageView",
            "android.widget.Button",
            "android.widget.EditText",
            "android.widget.Spinner",
            "android.widget.ImageButton",
            "android.widget.CheckBox",
            "android.widget.RadioButton",
            "android.widget.ToggleButton",
            "android.widget.AutoCompleteTextView",
            "android.widget.MultiAutoCompleteTextView",
            "android.widget.RatingBar",
            "android.widget.SeekBar",
            "android.widget.CheckedTextView"
        )
    }

    override fun applicableSuperClasses(): List<String>? {
        return DISALLOWED_CLASSES.toList()
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        if (!context.evaluator.isProjectClass(declaration)) {
            return
        }

        val qualifiedName = declaration.qualifiedName
        if (qualifiedName != null && (
            qualifiedName.startsWith("android.") ||
            qualifiedName.startsWith("androidx.") ||
            qualifiedName.startsWith("com.android.") ||
            qualifiedName.startsWith("com.google.android.material.")
        )) {
            return
        }

        val superClass = declaration.superClass ?: return
        val superName = superClass.qualifiedName ?: return

        if (superName in DISALLOWED_CLASSES) {
            val simpleName = superName.substringAfterLast('.')
            val useAndroidX = context.project.isAndroidx
            val prefix = if (useAndroidX) "androidx.appcompat.widget" else "android.support.v7.widget"
            val replacement = "$prefix.AppCompat$simpleName"

            val superTypeRef = declaration.uastSuperTypes.firstOrNull {
                val resolved = context.evaluator.getTypeClass(it.type)
                resolved?.qualifiedName == superName
            }

            val location = superTypeRef?.let { context.getLocation(it) } ?: context.getNameLocation(declaration)

            val fix = superTypeRef?.let {
                fix().replace()
                    .name("Extend $replacement instead")
                    .range(context.getLocation(it))
                    .with(replacement)
                    .shortenNames()
                    .build()
            }

            val message = "This custom view should extend `$replacement` instead of `$superName`"
            context.report(
                issue = ISSUE,
                scope = declaration,
                location = location,
                message = message,
                quickfixData = fix
            )
        }
    }
}