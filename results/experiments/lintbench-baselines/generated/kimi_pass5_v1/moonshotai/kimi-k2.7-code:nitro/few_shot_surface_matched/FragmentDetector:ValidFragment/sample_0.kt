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

class FragmentDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val ANDROID_APP_FRAGMENT = "android.app.Fragment"
    private const val SUPPORT_V4_FRAGMENT = "android.support.v4.app.Fragment"

    @JvmField
    val VALID_FRAGMENT =
      Issue.create(
        id = "ValidFragment",
        briefDescription = "Fragment not instantiatable",
        explanation =
          """
                Every fragment must have an empty constructor so it can be instantiated when its activity's state is restored. It is strongly recommended that subclasses do not have other constructors with parameters, since these constructors will not be called when the fragment is re-instantiated; instead, arguments can be supplied by the caller with `setArguments(Bundle)` and later retrieved by the Fragment with `getArguments()`.

                This check does not apply to `androidx.fragment.app.Fragment`, which can use `FragmentFactory` as of version 1.1.
            """.trimIndent(),
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.ERROR,
        implementation = Implementation(FragmentDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun applicableSuperClasses(): List<String> =
    listOf(ANDROID_APP_FRAGMENT, SUPPORT_V4_FRAGMENT)

  override fun visitClass(context: JavaContext, declaration: UClass) {
    val psiClass = declaration.javaPsi

    if (psiClass.isInterface || psiClass.isEnum || psiClass.isAnnotationType) {
      return
    }
    if (psiClass.name == null || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return
    }

    if (psiClass.containingClass != null && !psiClass.hasModifierProperty(PsiModifier.STATIC)) {
      val message = "Fragment inner classes should be static"
      context.report(
        Incident(VALID_FRAGMENT, declaration, context.getNameLocation(declaration), message)
      )
      return
    }

    if (!psiClass.hasModifierProperty(PsiModifier.PUBLIC)) {
      val message = "This fragment class should be public"
      context.report(
        Incident(VALID_FRAGMENT, declaration, context.getNameLocation(declaration), message)
      )
      return
    }

    val constructors = psiClass.methods.filter { it.isConstructor }
    if (constructors.isEmpty()) {
      return
    }

    val hasPublicNoArg =
      constructors.any {
        it.hasModifierProperty(PsiModifier.PUBLIC) && it.parameterList.parametersCount == 0
      }
    val hasArgConstructors =
      constructors.any { it.parameterList.parametersCount > 0 }

    if (!hasPublicNoArg) {
      val message = "This fragment should provide a public default constructor"
      context.report(
        Incident(VALID_FRAGMENT, declaration, context.getNameLocation(declaration), message)
      )
    } else if (hasArgConstructors) {
      val message =
        "Avoid non-default constructors with fragments: use `setArguments(Bundle)` instead"
      context.report(
        Incident(VALID_FRAGMENT, declaration, context.getNameLocation(declaration), message)
      )
    }
  }
}