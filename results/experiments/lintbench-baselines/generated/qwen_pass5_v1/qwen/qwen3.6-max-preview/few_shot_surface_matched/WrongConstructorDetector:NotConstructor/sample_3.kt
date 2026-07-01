package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
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
    val ISSUE = Issue.create(
      id = "NotConstructor",
      briefDescription = "Method looks like a constructor but isn't",
      explanation = "This check catches methods that look like they were intended to be constructors, but aren't.",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(WrongConstructorDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>>? {
    return listOf(UMethod::class.java)
  }

  override fun createUastHandler(context: JavaContext): UElementHandler? {
    return object : UElementHandler() {
      override fun visitMethod(node: UMethod) {
        val containingClass = node.containingClass ?: return
        if (node.isConstructor) return
        if (node.name != containingClass.name) return

        context.report(
          ISSUE,
          node,
          context.getLocation(node),
          "This method looks like a constructor but has a return type, making it a regular method."
        )
      }
    }
  }
}