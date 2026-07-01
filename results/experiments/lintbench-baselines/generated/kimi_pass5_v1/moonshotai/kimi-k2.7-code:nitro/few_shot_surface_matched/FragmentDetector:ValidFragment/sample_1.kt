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
    private val FRAGMENT_CLASSES = listOf(
      "android.app.Fragment",
      "android.support.v4.app.Fragment",
      "androidx.fragment.app.Fragment"
    )

    @JvmField
    val VALID_FRAGMENT = Issue.create(
      id = "ValidFragment",
      briefDescription = "Fragment not instantiatable",
      explanation =
        """
                Every fragment must have a public no-arg constructor so the framework can instantiate it when restoring the activity's state. Fragments that are non-static inner classes, not public, or that lack a public no-arg constructor are not valid.

                It is strongly recommended that fragments do not define constructors with parameters; use setArguments(Bundle) instead. Note that this requirement is relaxed for androidx.fragment.app.Fragment when used with FragmentFactory.
            """,
      category = Category.CORRECTNESS,
      priority = 8,
      severity = Severity.ERROR,
      implementation = Implementation(FragmentDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )
  }

  override fun getApplicableSuperClasses(): List<String> = FRAGMENT_CLASSES

  override fun visitClass(context: JavaContext, classNode: UClass) {
    if (classNode.isInterface || classNode.isEnum || classNode.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return
    }

    val containingClass = classNode.containingClass
    if (containingClass != null && !classNode.hasModifierProperty(PsiModifier.STATIC)) {
      reportIncident(
        context,
        classNode,
        "This fragment is a non-static inner class; fragments should be top-level or static nested classes so they can be re-instantiated."
      )
      return
    }

    if (!classNode.hasModifierProperty(PsiModifier.PUBLIC)) {
      reportIncident(context, classNode, "Fragment classes must be public.")
      return
    }

    val constructors = classNode.constructors
    if (constructors.isEmpty()) {
      // Implicit public no-arg constructor; valid.
      return
    }

    val hasPublicNoArg = constructors.any { it.isPublicNoArgConstructor() }
    if (!hasPublicNoArg) {
      reportIncident(context, classNode, "Fragment must provide a public no-arg constructor.")
    }

    for (constructor in constructors) {
      if (!constructor.isNoArgConstructor()) {
        reportIncident(
          context,
          constructor,
          "Avoid non-default constructors in fragments: use setArguments(Bundle) instead."
        )
      }
    }
  }

  private fun UMethod.isPublicNoArgConstructor(): Boolean {
    return isConstructor && hasModifierProperty(PsiModifier.PUBLIC) && parameterList.parameters.isEmpty()
  }

  private fun UMethod.isNoArgConstructor(): Boolean {
    return isConstructor && parameterList.parameters.isEmpty()
  }

  private fun reportIncident(context: JavaContext, node: UClass, message: String) {
    val location = context.getNameLocation(node)
    context.report(Incident(VALID_FRAGMENT, node, location, message))
  }

  private fun reportIncident(context: JavaContext, node: UMethod, message: String) {
    val location = context.getNameLocation(node)
    context.report(Incident(VALID_FRAGMENT, node, location, message))
  }
}