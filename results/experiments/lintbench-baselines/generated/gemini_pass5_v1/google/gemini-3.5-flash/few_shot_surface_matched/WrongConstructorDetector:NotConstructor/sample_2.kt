package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UMethod

class WrongConstructorDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "NotConstructor",
        briefDescription = "Method looks like a constructor but is not",
        explanation =
          """
                Constructors do not have a return type. If you specify a return type \
                (even `void`), the method is treated as a regular method, not a constructor. \
                This is usually a typo.
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
        if (node.isConstructor) {
          return
        }
        val name = node.name
        val containingClass = node.containingClass ?: return
        val className = containingClass.name ?: return
        if (name == className) {
          val message = "Method `$name` has the same name as its class; did you mean to make it a constructor?"
          context.report(
            Incident(ISSUE, node, context.getNameLocation(node), message)
          )
        }
      }
    }
  }
}