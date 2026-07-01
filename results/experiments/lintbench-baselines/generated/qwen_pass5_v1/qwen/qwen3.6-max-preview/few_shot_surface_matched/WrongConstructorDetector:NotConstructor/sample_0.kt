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
      explanation =
        "This method has the same name as its containing class, which makes it look like a " +
          "constructor, but it has a return type. Constructors cannot have a return type. " +
          "Either remove the return type to make it a constructor, or rename the method.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(WrongConstructorDetector::class.java, Scope.JAVA_FILE_SCOPE),
    )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      override fun visitMethod(node: UMethod) {
        val cls = node.containingClass ?: return
        val className = cls.name ?: return
        if (node.name != className) return
        if (node.returnType == null) return
        if (node.isStatic) return
        if (node.isConstructor) return

        val location = context.getNameLocation(node)
        val message =
          "This method looks like a constructor but has a return type. Constructors cannot have a return type."
        context.report(ISSUE, location, message)
      }
    }
  }
}