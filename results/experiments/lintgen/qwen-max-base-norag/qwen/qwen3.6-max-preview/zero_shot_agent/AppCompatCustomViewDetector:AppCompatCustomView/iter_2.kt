package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UClass
import org.jetbrains.uast.visitor.AbstractUastVisitor

class AppCompatCustomViewDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<UClass>> = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): AbstractUastVisitor {
        return object : AbstractUastVisitor() {
            override fun visitClass(node: UClass): Boolean {
                if (node.isInterface || node.isAnnotationType || node.isEnum) return false

                for (superTypeRef in node.uastSuperTypes) {
                    val psiClass = context.evaluator.getTypeClass(superTypeRef.type) ?: continue
                    val qualifiedName = psiClass.qualifiedName ?: continue
                    val replacement = REPLACEMENTS[qualifiedName] ?: continue

                    val sourcePsi = superTypeRef.sourcePsi ?: continue
                    val location = context.getLocation(sourcePsi)
                    val message = "Custom view should extend `$replacement` instead of `$qualifiedName`"

                    context.report(
                        ISSUE,
                        location,
                        message,
                        fix()
                            .replace()
                            .text(sourcePsi.text)
                            .with(replacement.substringAfterLast('.'))
                            .imports(replacement)
                            .build()
                    )
                }
                return false
            }
        }
    }

    companion object {
        private val REPLACEMENTS = mapOf(
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
            "android.widget.Switch" to "androidx.appcompat.widget.SwitchCompat"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AppCompatCustomView",
            briefDescription = "AppCompat Custom Widgets",
            explanation = """
                In order to support features such as tinting, the appcompat library will \
                automatically load special appcompat replacements for the builtin widgets. \
                However, this does not work for your own custom views.

                Instead of extending the `android.widget` classes directly, you should \
                instead extend one of the delegate classes in `androidx.appcompat.widget`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppCompatCustomViewDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}