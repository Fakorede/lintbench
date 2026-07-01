package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UMethod

class WrongConstructorDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "NotConstructor",
        briefDescription = "Not a Constructor",
        explanation =
          """
                This method has the same name as its containing class, which makes it look like a constructor. \
                However, it has a return type, so it is actually a normal method rather than a constructor. \
                Remove the return type if you intended it to be a constructor.
            """.trimIndent(),
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

        val methodName = node.name ?: return
        val className = node.getContainingUClass()?.name ?: return

        if (methodName != className) {
          return
        }

        val message = "$methodName looks like a constructor but is a method"
        context.report(ISSUE, node, context.getNameLocation(node.psi), message)
      }
    }
  }
}