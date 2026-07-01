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
import org.jetbrains.uast.UClass

class AppCompatCustomViewDetector : Detector(), SourceCodeScanner {

  companion object {
    private val APPLICABLE_SUPER_CLASSES = listOf(
      "android.widget.TextView",
      "android.widget.ImageView",
      "android.widget.Button",
      "android.widget.EditText",
      "android.widget.RadioButton",
      "android.widget.CheckBox",
      "android.widget.CheckedTextView",
      "android.widget.AutoCompleteTextView",
      "android.widget.MultiAutoCompleteTextView",
      "android.widget.Spinner",
      "android.widget.RatingBar",
      "android.widget.SeekBar",
      "android.widget.ImageButton",
      "android.widget.ToggleButton",
      "android.widget.Switch"
    )

    @JvmField
    val ISSUE = Issue.create(
      id = "AppCompatCustomView",
      briefDescription = "AppCompat Custom Widgets",
      explanation = """
        In order to support features such as tinting, the appcompat library will automatically load special appcompat replacements for the builtin widgets. However, this does not work for your own custom views.
        Instead of extending the `android.widget` classes directly, you should instead extend one of the delegate classes in `androidx.appcompat.widget.AppCompatTextView`.
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(AppCompatCustomViewDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )
  }

  override fun applicableSuperClasses(): List<String>? = APPLICABLE_SUPER_CLASSES

  override fun visitClass(context: JavaContext, declaration: UClass) {
    val directSuperClass = context.evaluator.getSuperClass(declaration) ?: return
    val superQualifiedName = directSuperClass.qualifiedName ?: return
    if (superQualifiedName !in APPLICABLE_SUPER_CLASSES) return

    val simpleName = superQualifiedName.substringAfterLast('.')
    val replacement = if (superQualifiedName == "android.widget.Switch") {
      "androidx.appcompat.widget.SwitchCompat"
    } else {
      "androidx.appcompat.widget.AppCompat$simpleName"
    }

    val superTypeRef = declaration.uastSuperTypes.firstOrNull()
    val location = superTypeRef?.sourcePsi?.let { context.getLocation(it) }
      ?: context.getLocation(declaration)

    val message = "Custom view extends `$simpleName` instead of `${replacement.substringAfterLast('.')}`"
    context.report(Incident(ISSUE, declaration, location, message))
  }
}