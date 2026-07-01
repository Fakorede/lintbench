package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UElementHandler
import com.intellij.psi.PsiClassType
import org.jetbrains.uast.UClass

class AppCompatCustomViewDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                for (superType in node.uastSuperTypes) {
                    val psiType = superType.type as? PsiClassType ?: continue
                    val psiClass = psiType.resolve() ?: continue
                    val qualifiedName = psiClass.qualifiedName ?: continue
                    if (!qualifiedName.startsWith(ANDROID_WIDGET_PREFIX)) continue

                    val className = qualifiedName.substring(ANDROID_WIDGET_PREFIX.length)
                    val replacement = WIDGET_TO_APPCOMPAT[className] ?: continue

                    val message = buildString {
                        append("This custom view should extend ")
                        append(APPCOMPAT_PREFIX)
                        append(replacement)
                        append(" instead of ")
                        append(className)
                        append(".")
                    }

                    context.report(
                        ISSUE,
                        superType,
                        context.getLocation(superType),
                        message
                    )
                }
            }
        }
    }

    companion object {
        private const val ANDROID_WIDGET_PREFIX = "android.widget."
        private const val APPCOMPAT_PREFIX = "androidx.appcompat.widget."

        private val WIDGET_TO_APPCOMPAT = mapOf(
            "TextView" to "AppCompatTextView",
            "EditText" to "AppCompatEditText",
            "Button" to "AppCompatButton",
            "ImageButton" to "AppCompatImageButton",
            "ImageView" to "AppCompatImageView",
            "CheckBox" to "AppCompatCheckBox",
            "RadioButton" to "AppCompatRadioButton",
            "Spinner" to "AppCompatSpinner",
            "Switch" to "AppCompatSwitch",
            "SeekBar" to "AppCompatSeekBar",
            "AutoCompleteTextView" to "AppCompatAutoCompleteTextView",
            "MultiAutoCompleteTextView" to "AppCompatMultiAutoCompleteTextView",
            "CheckedTextView" to "AppCompatCheckedTextView",
            "RatingBar" to "AppCompatRatingBar",
            "ToggleButton" to "AppCompatToggleButton"
        )

        val ISSUE = Issue.create(
            id = "AppCompatCustomView",
            briefDescription = "Appcompat Custom Widgets",
            explanation = """
                In order to support features such as tinting, the appcompat library will automatically
                load special appcompat replacements for the builtin widgets. However, this does not work
                for your own custom views.

                Instead of extending the `android.widget` classes directly, you should instead extend one
                of the delegate classes in `androidx.appcompat.widget` (such as `AppCompatTextView`).
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