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
    @JvmField
    val ISSUE =
      Issue.create(
        id = "AppCompatCustomView",
        briefDescription = "Appcompat Custom Widgets",
        explanation =
          """
                In order to support features such as tinting, the appcompat library will automatically
                load special appcompat replacements for the builtin widgets. However, this does not
                work for your own custom views.

                Instead of extending the `android.widget` classes directly, you should extend one of
                the delegate classes in `androidx.appcompat.widget` (for example,
                `AppCompatTextView`).
            """,
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.ERROR,
        implementation = Implementation(AppCompatCustomViewDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )

    private val WIDGETS =
      listOf(
        "android.widget.AbsSeekBar",
        "android.widget.AutoCompleteTextView",
        "android.widget.Button",
        "android.widget.CheckBox",
        "android.widget.CheckedTextView",
        "android.widget.CompoundButton",
        "android.widget.EditText",
        "android.widget.ImageButton",
        "android.widget.ImageView",
        "android.widget.MultiAutoCompleteTextView",
        "android.widget.RadioButton",
        "android.widget.RatingBar",
        "android.widget.SeekBar",
        "android.widget.Spinner",
        "android.widget.Switch",
        "android.widget.TextView",
        "android.widget.ToggleButton",
      )
  }

  override fun applicableSuperClasses(): List<String>? = WIDGETS

  override fun visitClass(context: JavaContext, declaration: UClass) {
    if (declaration.isInterface || declaration.isEnum || declaration.isAnnotationType) {
      return
    }

    val superClassName = declaration.superClass?.qualifiedName ?: return
    if (superClassName !in WIDGETS) {
      return
    }

    val message =
      "This custom view extends $superClassName directly; extend the corresponding AppCompat widget from androidx.appcompat.widget instead."
    val location = context.getNameLocation(declaration)
    context.report(Incident(ISSUE, declaration, location, message))
  }
}