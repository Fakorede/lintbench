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
    val ISSUE =
      Issue.create(
        id = "AppCompatCustomView",
        briefDescription = "AppCompat Custom Widgets",
        explanation =
          """
                In order to support features such as tinting, the appcompat library will automatically load special appcompat replacements for the builtin widgets. However, this does not work for your own custom views.

                Instead of extending the `android.widget` classes directly, you should instead extend one of the delegate classes in `androidx.appcompat.widget.AppCompatTextView`.
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = Implementation(AppCompatCustomViewDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )

    private val WIDGET_CLASSES =
      listOf(
        "android.widget.TextView",
        "android.widget.ImageView",
        "android.widget.ImageButton",
        "android.widget.Button",
        "android.widget.EditText",
        "android.widget.Spinner",
        "android.widget.CheckBox",
        "android.widget.RadioButton",
        "android.widget.CheckedTextView",
        "android.widget.AutoCompleteTextView",
        "android.widget.MultiAutoCompleteTextView",
        "android.widget.ToggleButton",
        "android.widget.Switch",
        "android.widget.RatingBar",
        "android.widget.SeekBar",
      )
  }

  override fun applicableSuperClasses(): List<String>? = WIDGET_CLASSES

  override fun visitClass(context: JavaContext, declaration: UClass) {
    val qualifiedName = declaration.qualifiedName ?: return
    if (qualifiedName.startsWith("android.widget.") || qualifiedName.startsWith("androidx.appcompat.")) {
      return
    }

    val superClassName =
      declaration.uastSuperTypes
        .mapNotNull { (it.resolve() as? PsiClass)?.qualifiedName }
        .firstOrNull { it in WIDGET_CLASSES } ?: return

    val replacement =
      when (superClassName) {
        "android.widget.Switch" -> "androidx.appcompat.widget.SwitchCompat"
        else -> superClassName.replace("android.widget.", "androidx.appcompat.widget.AppCompat")
      }

    val location = context.getNameLocation(declaration)
    val message = "This custom view should extend $replacement instead"
    context.report(Incident(ISSUE, declaration, location, message))
  }
}