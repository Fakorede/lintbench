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
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod

class FragmentDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val VALID_FRAGMENT =
      Issue.create(
        id = "ValidFragment",
        briefDescription = "Fragment not instantiatable",
        explanation =
          """
                Every fragment must have an empty constructor, so it can be instantiated when restoring its activity's state. It is strongly recommended that subclasses do not have other constructors with parameters, since these constructors will not be called when the fragment is re-instantiated; instead, arguments can be supplied by the caller with `setArguments(Bundle)` and later retrieved by the Fragment with `getArguments()`.

                Note that this is no longer true when you are using `androidx.fragment.app.Fragment`; with the `FragmentFactory` you can supply any arguments you want (as of version androidx 1.1).
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = Implementation(FragmentDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun applicableSuperClasses(): List<String> =
    listOf(
      "android.app.Fragment",
      "android.support.v4.app.Fragment",
    )

  override fun visitClass(context: JavaContext, declaration: UClass) {
    if (declaration.isInterface || declaration.isEnum || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return
    }

    val constructors = declaration.methods.filter { it.isConstructor }
    if (constructors.isEmpty()) {
      return
    }

    val hasPublicNoArg =
      constructors.any { method ->
        method.hasModifierProperty(PsiModifier.PUBLIC) && method.parameterList.parameters.isEmpty()
      }

    if (!hasPublicNoArg) {
      val name = declaration.name ?: "This fragment"
      val message = "$name must have a public no-arg constructor so it can be re-instantiated"
      val location = context.getNameLocation(declaration)
      context.report(Incident(VALID_FRAGMENT, declaration, location, message))
    }

    for (constructor in constructors) {
      if (constructor.parameterList.parameters.isNotEmpty()) {
        val message = "Avoid passing arguments in fragment constructors; use setArguments(Bundle) instead"
        val location = context.getLocation(constructor)
        context.report(
          Incident(VALID_FRAGMENT, constructor, location, message)
            .overrideSeverity(Severity.WARNING)
        )
      }
    }
  }
}