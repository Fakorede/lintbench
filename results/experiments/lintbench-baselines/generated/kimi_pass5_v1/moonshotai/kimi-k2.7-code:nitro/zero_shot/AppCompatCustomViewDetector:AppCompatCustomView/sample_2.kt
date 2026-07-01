package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UClass

class AppCompatCustomViewDetector : Detector(), Detector.UastScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            AppCompatCustomViewDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AppCompatCustomView",
            briefDescription = "Appcompat custom widgets",
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
            implementation = IMPLEMENTATION
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
            "android.widget.TextView"
        )

        private fun getAppCompatDelegate(widget: String): String {
            val simpleName = widget.substring(widget.lastIndexOf('.') + 1)
            return "androidx.appcompat.widget.AppCompat$simpleName"
        }
    }

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                if (node.isInterface || node.isEnum || node.isAnnotationType) {
                    return
                }
                if (node.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return
                }

                val qualifiedName = node.qualifiedName ?: return
                if (qualifiedName.startsWith("android.") || qualifiedName.startsWith("androidx.")) {
                    return
                }

                val superClass = node.superClass ?: return
                val superName = superClass.qualifiedName ?: return
                if (superName !in WIDGETS) {
                    return
                }

                val delegate = getAppCompatDelegate(superName)
                val message = "This custom view should extend $delegate instead of $superName"
                context.report(
                    ISSUE,
                    node,
                    context.getNameLocation(node),
                    message
                )
            }
        }
    }
}