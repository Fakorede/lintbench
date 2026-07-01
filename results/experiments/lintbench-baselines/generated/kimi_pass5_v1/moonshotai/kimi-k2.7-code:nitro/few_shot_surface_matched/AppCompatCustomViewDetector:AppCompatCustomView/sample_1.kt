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
    private val WIDGETS =
      listOf(
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
        "android.widget.TextView",
      )

    @JvmField
    val APP_COMPAT_CUSTOM_VIEW =
      Issue.create(
        id = "AppCompatCustomView",
        briefDescription = "AppCompat Custom Widgets",
        explanation =
          """
                In order to support features such as tinting, the appcompat library will automatically load special appcompat replacements for the builtin widgets. However, this does not work for your own custom views.

                Instead of extending the `android.widget` classes directly, you should instead extend the corresponding AppCompat delegate class from `androidx.appcompat.widget`.
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = Implementation(AppCompatCustomViewDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun applicableSuperClasses(): List<String> = WIDGETS

  override fun visitClass(context: JavaContext, node: UClass) {
    if (node.name == null) return

    val psiClass: PsiClass = node.javaPsi
    if (psiClass.isInterface || psiClass.isAnnotationType) return

    val qualifiedName = node.qualifiedName
    if (
      qualifiedName == null ||
        qualifiedName.startsWith("android.") ||
        qualifiedName.startsWith("androidx.appcompat.widget.") ||
        qualifiedName.startsWith("android.support.v7.widget.")
    ) {
      return
    }

    val superClass = psiClass.superClass ?: return
    val superName = superClass.qualifiedName ?: return
    if (superName !in WIDGETS) return

    val simpleName = superName.substring(superName.lastIndexOf('.') + 1)
    val appCompatClass = "androidx.appcompat.widget.AppCompat$simpleName"
    val message = "This custom view should extend `$appCompatClass` instead of `$superName`"

    context.report(
      Incident(
        issue = APP_COMPAT_CUSTOM_VIEW,
        scope = node,
        location = context.getNameLocation(node),
        message = message,
      )
    )
  }
}