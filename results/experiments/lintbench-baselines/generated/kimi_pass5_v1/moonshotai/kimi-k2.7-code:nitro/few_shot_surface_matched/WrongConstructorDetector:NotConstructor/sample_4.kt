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
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UMethod

class WrongConstructorDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE: Issue =
      Issue.create(
        id = "NotConstructor",
        briefDescription = "Not a constructor",
        explanation =
          """
                A constructor must have the same name as its enclosing class and no return type.
                This method has the same name as its class but also declares a return type, so it
                is an ordinary method rather than a constructor. If you intended it to be a
                constructor, remove the return type (including `void`).
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(WrongConstructorDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun getApplicableUastTypes() = listOf(UMethod::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      override fun visitMethod(node: UMethod) {
        if (!context.isEnabled(ISSUE)) return
        if (node.isConstructor) return
        if (node.sourcePsi !is PsiMethod) return
        if (node.hasModifierProperty(PsiModifier.STATIC)) return

        val containingClass = node.containingClass ?: return
        if (containingClass.isInterface || containingClass.isAnnotationType) return

        val methodName = node.name ?: return
        val className = containingClass.name ?: return
        if (methodName != className) return

        if (node.findAnnotation("java.lang.Override") != null) return

        val location = context.getLocation(node)
        val message =
          "Method '$methodName' has the same name as its enclosing class but is not a constructor (it has a return type). If this was intended to be a constructor, remove the return type."
        context.report(Incident(ISSUE, node, location, message))
      }
    }
  }
}