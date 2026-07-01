package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UClass

class AppCompatCustomViewDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            AppCompatCustomViewDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        private val APPLICABLE_SUPERCLASSES = listOf(
            "android.widget.TextView",
            "android.widget.Button",
            "android.widget.EditText",
            "android.widget.AutoCompleteTextView",
            "android.widget.MultiAutoCompleteTextView",
            "android.widget.CheckBox",
            "android.widget.RadioButton",
            "android.widget.CheckedTextView",
            "android.widget.ToggleButton",
            "android.widget.Switch",
            "android.widget.ImageView",
            "android.widget.ImageButton",
            "android.widget.Spinner",
            "android.widget.ListView",
            "android.widget.GridView",
            "android.widget.ProgressBar",
            "android.widget.SeekBar",
            "android.widget.RatingBar",
            "android.widget.ScrollView",
            "android.widget.HorizontalScrollView",
            "android.widget.FrameLayout",
            "android.widget.LinearLayout",
            "android.widget.RelativeLayout",
            "android.widget.TableLayout",
            "android.widget.TableRow"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AppCompatCustomView",
            briefDescription = "Appcompat Custom Widgets",
            explanation = "In order to support features such as tinting, the appcompat library will automatically load special appcompat replacements for the builtin widgets. However, this does not work for your own custom views. Instead of extending the `android.widget` classes directly, you should instead extend one of the delegate classes in `androidx.appcompat.widget`.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableSuperClasses(): List<String>? = APPLICABLE_SUPERCLASSES

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val qualifiedName = declaration.qualifiedName ?: return
        if (qualifiedName.startsWith("android.") || qualifiedName.startsWith("androidx.")) {
            return
        }

        for (superType in declaration.uastSuperTypes) {
            val superClass = context.evaluator.getTypeClass(superType)
            val superQualifiedName = superClass?.qualifiedName ?: continue
            if (superQualifiedName in APPLICABLE_SUPERCLASSES) {
                val simpleName = superQualifiedName.substringAfterLast('.')
                val appCompatName = if (simpleName == "Switch") "SwitchCompat" else "AppCompat$simpleName"
                val suggestion = "androidx.appcompat.widget.$appCompatName"
                val message = "This custom view should extend `$suggestion` instead of `$superQualifiedName`"
                context.report(ISSUE, declaration, context.getNameLocation(declaration), message)
                break
            }
        }
    }
}