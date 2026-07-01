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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UMethod

class WrongConstructorDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val NotConstructor =
      Issue.create(
        id = "NotConstructor",
        briefDescription = "Method is not a constructor",
        explanation =
          "A method that has the same name as its containing class but declares a return type is not a constructor. If a constructor was intended, remove the return type.",
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(WrongConstructorDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler =
    object : UElementHandler() {
      override fun visitMethod(node: UMethod) {
        if (node.isConstructor) return

        val containingClass = node.containingClass ?: return
        val psiClass = containingClass.javaPsi
        if (psiClass != null && (psiClass.isInterface || psiClass.isAnnotationType)) return

        if (node.name != containingClass.name) return
        if (node.returnType == null) return

        val message =
          "Method '${node.name}' has the same name as its class but declares a return type, so it is not a constructor."
        context.report(
          Incident(
            NotConstructor,
            node,
            context.getNameLocation(node),
            message,
          )
        )
      }
    }
}