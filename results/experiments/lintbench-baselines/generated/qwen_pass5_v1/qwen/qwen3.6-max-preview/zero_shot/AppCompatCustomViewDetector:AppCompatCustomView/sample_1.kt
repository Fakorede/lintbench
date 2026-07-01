package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClass
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement

class AppCompatCustomViewDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                for (superTypeRef in node.uastSuperTypes) {
                    val superClass = superTypeRef.resolve() as? PsiClass ?: continue
                    if (superClass.isInterface) continue
                    val superQualifiedName = superClass.qualifiedName ?: continue
                    val replacement = APPCOMPAT_REPLACEMENTS[superQualifiedName] ?: continue

                    val packageName = node.qualifiedName?.substringBeforeLast('.') ?: ""
                    if (packageName.startsWith("androidx.appcompat.widget")) return

                    val location = context.getLocation(superTypeRef)
                    val shortName = superQualifiedName.substringAfterLast('.')
                    val replacementShort = replacement.substringAfterLast('.')

                    context.report(
                        ISSUE,
                        location,
                        "Use `$replacementShort` instead of `$shortName` to support tinting and other AppCompat features"
                    )
                    break
                }
            }
        }
    }

    companion object {
        private val APPCOMPAT_REPLACEMENTS = mapOf(
            "android.widget.TextView" to "androidx.appcompat.widget.AppCompatTextView",
            "android.widget.ImageView" to "androidx.appcompat.widget.AppCompatImageView",
            "android.widget.Button" to "androidx.appcompat.widget.AppCompatButton",
            "android.widget.EditText" to "androidx.appcompat.widget.AppCompatEditText",
            "android.widget.Spinner" to "androidx.appcompat.widget.AppCompatSpinner",
            "android.widget.ImageButton" to "androidx.appcompat.widget.AppCompatImageButton",
            "android.widget.CheckBox" to "androidx.appcompat.widget.AppCompatCheckBox",
            "android.widget.RadioButton" to "androidx.appcompat.widget.AppCompatRadioButton",
            "android.widget.CheckedTextView" to "androidx.appcompat.widget.AppCompatCheckedTextView",
            "android.widget.MultiAutoCompleteTextView" to "androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView",
            "android.widget.AutoCompleteTextView" to "androidx.appcompat.widget.AppCompatAutoCompleteTextView",
            "android.widget.ToggleButton" to "androidx.appcompat.widget.AppCompatToggleButton",
            "android.widget.RatingBar" to "androidx.appcompat.widget.AppCompatRatingBar",
            "android.widget.SeekBar" to "androidx.appcompat.widget.AppCompatSeekBar"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AppCompatCustomView",
            briefDescription = "AppCompat Custom Widgets",
            explanation = """
                In order to support features such as tinting, the appcompat library will automatically load special appcompat replacements for the builtin widgets. However, this does not work for your own custom views.

                Instead of extending the `android.widget` classes directly, you should instead extend one of the delegate classes in `androidx.appcompat.widget.AppCompatTextView`.
            """.trimIndent(),
            category = Category.COMPATIBILITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppCompatCustomViewDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}