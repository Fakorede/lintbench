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
    val ISSUE = Issue.create(
      id = "ValidFragment",
      briefDescription = "Fragment not instantiable",
      explanation = """
        Every fragment must have an empty constructor, so it can be instantiated when restoring its activity's state. It is strongly recommended that subclasses do not have other constructors with parameters, since these constructors will not be called when the fragment is re-instantiated; instead, arguments can be supplied by the caller with setArguments(Bundle) and later retrieved by the Fragment with getArguments().
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(FragmentDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )
  }

  override fun applicableSuperClasses(): List<String>? = listOf("android.app.Fragment")

  override fun visitClass(context: JavaContext, declaration: UClass) {
    if (declaration.isAbstract || declaration.isInterface) return

    val constructors = declaration.methods.filter { it.isConstructor }
    if (constructors.isEmpty()) return

    var hasPublicEmptyConstructor = false
    for (constructor in constructors) {
      val isPublic = constructor.hasModifierProperty(PsiModifier.PUBLIC)
      val isEmpty = constructor.uastParameters.isEmpty()

      if (isPublic && isEmpty) {
        hasPublicEmptyConstructor = true
      } else if (!isEmpty) {
        val location = context.getLocation(constructor)
        val message = "Avoid non-empty constructors in fragments; use setArguments(Bundle) instead"
        context.report(Incident(ISSUE, constructor, location, message))
      }
    }

    if (!hasPublicEmptyConstructor) {
      val location = context.getLocation(declaration)
      val message = "Fragment must have a public empty constructor"
      context.report(Incident(ISSUE, declaration, location, message))
    }
  }
}