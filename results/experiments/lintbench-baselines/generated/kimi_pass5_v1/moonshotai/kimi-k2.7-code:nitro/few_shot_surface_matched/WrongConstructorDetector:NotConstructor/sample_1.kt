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
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UMethod

class WrongConstructorDetector : Detector(), SourceCodeScanner {

  override fun getApplicableUastTypes() = listOf(UMethod::class.java)

  override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
    override fun visitMethod(node: UMethod) {
      if (node.isConstructor) return
      if (node.language.id != "JAVA") return

      val methodName = node.name ?: return
      val className = node.containingClass?.name ?: return
      if (methodName != className) return
      if (node.returnType == null) return

      val message =
        "Method '$methodName' has the same name as its containing class and declares a return type, so it is not a constructor. Remove the return type to make it a constructor."
      val location = context.getNameLocation(node)

      context.report(Incident(ISSUE, node, location, message))
    }
  }

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "NotConstructor",
        briefDescription = "Not a Constructor",
        explanation =
          """
                This method has the same name as its containing class and declares a return type. \
                In Java that makes it an ordinary method, not a constructor. If you intended this \
                to be a constructor, remove the return type.
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(WrongConstructorDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }
}