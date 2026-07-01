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
    val APP_COMPAT_CUSTOM_VIEW =
      Issue.create(
        id = "AppCompatCustomView",
        briefDescription = "Appcompat Custom Widgets",
        explanation =
          """
                In order to support features such as tinting, the appcompat library will
                automatically load special appcompat replacements for the builtin widgets.
                However, this does not work for your own custom views.

                Instead of extending the `android.widget` classes directly, you should
                instead extend one of the delegate classes in `androidx.appcompat.widget`
                (such as `AppCompatTextView`).
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.ERROR,
        implementation = Implementation(AppCompatCustomViewDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )

    private val APP_WIDGET_SUPER_CLASSES =
      listOf(
        "android.widget.TextView",
        "android.widget.Button",
        "android.widget.CheckBox",
        "android.widget.CheckedTextView",
        "android.widget.EditText",
        "android.widget.ImageButton",
        "android.widget.ImageView",
        "android.widget.RadioButton",
        "android.widget.RatingBar",
        "android.widget.SeekBar",
        "android.widget.Spinner",
        "android.widget.Switch",
        "android.widget.AutoCompleteTextView",
        "android.widget.MultiAutoCompleteTextView"
      )

    private val APPCOMPAT_PACKAGES =
      listOf(
        "androidx.appcompat.widget.",
        "android.support.v7.widget.",
        "android.widget."
      )
  }

  override fun applicableSuperClasses(): List<String> = APP_WIDGET_SUPER_CLASSES

  override fun visitClass(context: JavaContext, declaration: UClass) {
    val qualifiedName = declaration.javaPsi.qualifiedName ?: return
    if (APPCOMPAT_PACKAGES.any { qualifiedName.startsWith(it) }) {
      return
    }

    val directSuper = declaration.javaPsi.extendsListTypes.firstOrNull()?.canonicalText ?: return
    val message =
      "This custom view extends $directSuper directly. To support features such as tinting when using AppCompat, extend the corresponding AppCompat delegate class from androidx.appcompat.widget instead."

    context.report(
      Incident(
        APP_COMPAT_CUSTOM_VIEW,
        declaration,
        context.getNameLocation(declaration),
        message
      )
    )
  }
}