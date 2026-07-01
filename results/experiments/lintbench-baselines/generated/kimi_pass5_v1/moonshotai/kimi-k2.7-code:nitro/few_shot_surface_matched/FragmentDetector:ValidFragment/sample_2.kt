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
    private const val ANDROID_APP_FRAGMENT = "android.app.Fragment"
    private const val SUPPORT_FRAGMENT = "android.support.v4.app.Fragment"
    private const val ANDROIDX_FRAGMENT = "androidx.fragment.app.Fragment"

    @JvmField
    val VALID_FRAGMENT =
      Issue.create(
        id = "ValidFragment",
        briefDescription = "Fragment not instantiatable",
        explanation =
          """
                Every fragment must have an empty constructor, so it can be instantiated when restoring its activity's state. It is strongly recommended that subclasses do not have other constructors with parameters, since these constructors will not be called when the fragment is re-instantiated; instead, arguments can be supplied by the caller with `setArguments(Bundle)` and later retrieved by the Fragment with `getArguments()`.

                Note that this is no longer true when you are using `androidx.fragment.app.Fragment`; with the `FragmentFactory` you can supply any arguments you want (as of version androidx version 1.1).
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = Implementation(FragmentDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableSuperClasses(): List<String> =
    listOf(ANDROID_APP_FRAGMENT, SUPPORT_FRAGMENT, ANDROIDX_FRAGMENT)

  override fun visitClass(context: JavaContext, declaration: UClass) {
    if (declaration.isInterface || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return
    }

    if (declaration.containingClass != null && !declaration.hasModifierProperty(PsiModifier.STATIC)) {
      val location = context.getNameLocation(declaration)
      val message = "Fragment inner classes should be static"
      context.report(Incident(VALID_FRAGMENT, declaration, location, message))
      return
    }

    val constructors = declaration.methods.filter { it.isConstructor }
    if (constructors.isEmpty()) {
      return
    }

    val hasDefault =
      constructors.any { constructor ->
        constructor.parameterList.parametersCount == 0 &&
          (constructor.hasModifierProperty(PsiModifier.PUBLIC) ||
            constructor.hasModifierProperty(PsiModifier.PROTECTED))
      }

    if (!hasDefault) {
      val location = context.getNameLocation(declaration)
      val message = "Fragment must provide a public no-arg constructor"
      context.report(Incident(VALID_FRAGMENT, declaration, location, message))
      return
    }

    if (!context.evaluator.extendsClass(declaration, ANDROIDX_FRAGMENT, false)) {
      for (constructor in constructors) {
        if (constructor.parameterList.parametersCount > 0) {
          val location = context.getLocation(constructor)
          val message =
            "Avoid non-default constructors with parameters in Fragments. Use `setArguments(Bundle)` instead."
          context.report(Incident(VALID_FRAGMENT, constructor, location, message))
        }
      }
    }
  }
}