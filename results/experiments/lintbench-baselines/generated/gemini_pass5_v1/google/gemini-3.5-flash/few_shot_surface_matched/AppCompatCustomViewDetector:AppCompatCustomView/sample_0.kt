package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import org.jetbrains.uast.UClass

class AppCompatCustomViewDetector : Detector(), SourceCodeScanner {

  companion object {
    private val MAPPING = mapOf(
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
        In order to support features such as tinting, the appcompat library will automatically load special appcompat replacements for the builtin widgets. However, this does not work for your own custom views.

        Instead of extending the `android.widget` classes directly, you should instead extend one of the delegate classes in `androidx.appcompat.widget.AppCompatTextView`.
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 4,
      severity = Severity.ERROR,
      implementation = Implementation(
        AppCompatCustomViewDetector::class.java,
        Scope.JAVA_FILE_SCOPE
      )
    )
  }

  override fun applicableSuperClasses(): List<String> {
    return MAPPING.keys.toList()
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    val superClass = declaration.javaPsi.superClass ?: return
    val superClassName = superClass.qualifiedName ?: return
    val replacement = MAPPING[superClassName] ?: return

    val qualifiedName = declaration.qualifiedName
    if (qualifiedName != null && (qualifiedName.startsWith("androidx.appcompat.") || qualifiedName.startsWith("android.support.v7.widget."))) {
      return
    }

    val message = "This custom view should extend `$replacement` instead of `$superClassName` to support features such as tinting"
    context.report(
      Incident(ISSUE, declaration, context.getNameLocation(declaration), message)
    )
  }
}