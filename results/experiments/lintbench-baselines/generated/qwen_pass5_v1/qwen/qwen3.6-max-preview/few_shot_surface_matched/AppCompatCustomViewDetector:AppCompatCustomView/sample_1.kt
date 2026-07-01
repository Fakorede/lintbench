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
    @JvmField
    val ISSUE = Issue.create(
      id = "AppCompatCustomView",
      briefDescription = "Custom view should extend AppCompat widget",
      explanation = """
        In order to support features such as tinting, the appcompat library will \
        automatically load special appcompat replacements for the builtin widgets. \
        However, this does not work for your own custom views.

        Instead of extending the `android.widget` classes directly, you should \
        instead extend one of the delegate classes in `androidx.appcompat.widget`.
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(AppCompatCustomViewDetector::class.java, Scope.JAVA_FILE_SCOPE),
      androidSpecific = true
    )

    private val APPCOMPAT_REPLACEMENTS = mapOf(
      "android.widget.TextView" to "androidx.appcompat.widget.AppCompatTextView",
      "android.widget.ImageView" to "androidx.appcompat.widget.AppCompatImageView",
      "android.widget.Button" to "androidx.appcompat.widget.AppCompatButton",
      "android.widget.EditText" to "androidx.appcompat.widget.AppCompatEditText",
      "android.widget.Spinner" to "androidx.appcompat.widget.AppCompatSpinner",
      "android.widget.CheckBox" to "androidx.appcompat.widget.AppCompatCheckBox",
      "android.widget.RadioButton" to "androidx.appcompat.widget.AppCompatRadioButton",
      "android.widget.Switch" to "androidx.appcompat.widget.SwitchCompat",
      "android.widget.SeekBar" to "androidx.appcompat.widget.AppCompatSeekBar",
      "android.widget.AutoCompleteTextView" to "androidx.appcompat.widget.AppCompatAutoCompleteTextView",
      "android.widget.MultiAutoCompleteTextView" to "androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView",
      "android.widget.CheckedTextView" to "androidx.appcompat.widget.AppCompatCheckedTextView",
      "android.widget.ImageButton" to "androidx.appcompat.widget.AppCompatImageButton",
      "android.widget.RatingBar" to "androidx.appcompat.widget.AppCompatRatingBar"
    )
  }

  override fun applicableSuperClasses(): List<String>? = APPCOMPAT_REPLACEMENTS.keys.toList()

  override fun visitClass(context: JavaContext, declaration: UClass) {
    val qualifiedName = declaration.qualifiedName ?: return
    if (qualifiedName.startsWith("androidx.appcompat.")) return

    for (superTypeRef in declaration.uastSuperTypes) {
      val superClass = superTypeRef.resolve() as? PsiClass ?: continue
      val superQualifiedName = superClass.qualifiedName ?: continue
      val replacement = APPCOMPAT_REPLACEMENTS[superQualifiedName] ?: continue

      val location = context.getLocation(superTypeRef)
      val message = "This custom view should extend `$replacement` instead of `$superQualifiedName`"
      context.report(Incident(ISSUE, declaration, location, message))
      return
    }
  }
}