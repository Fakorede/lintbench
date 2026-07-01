package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.getParentOfType

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

  override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
    return folderType == com.android.resources.ResourceFolderType.LAYOUT
  }

  override fun getApplicableAttributes(): Collection<String> {
    return listOf(ON_CLICK)
  }

  override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
    val methodName = attribute.value ?: return
    if (methodName.isBlank()) return

    val methods = context.evaluator.findMethods(context, methodName, 1)
    for (method in methods) {
      val parameters = method.parameterList.parameters
      if (parameters.size != 1) continue
      val paramType = parameters[0].type
      if (isViewSubclass(paramType)) {
        val message =
          "Add explicit cast to `${paramType.presentableText}` for the `${method.name}` handler; " +
          "the framework passes a `View` and a cast is required for Java 8 compatibility."
        context.report(ISSUE, attribute, context.getValueLocation(attribute), message)
      }
    }
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf("findViewById")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.app.Activity") &&
        !context.evaluator.isMemberInSubClassOf(method, "android.view.View")) {
      return
    }

    val argumentParent = node.getParentOfType(UCallExpression::class.java, true) ?: return
    val index = argumentParent.valueArguments.indexOf(node)
    if (index < 0) return

    val targetMethod = argumentParent.resolve() ?: return
    val paramType = targetMethod.parameterList.getParameter(index)?.type ?: return
    if (isViewSubclass(paramType)) {
      reportCastNeeded(context, node, paramType)
    }
  }

  private fun isViewSubclass(type: PsiType): Boolean {
    val text = type.canonicalText
    return text != ANDROID_VIEW && (text.startsWith("android.widget.") ||
        (text.startsWith("android.view.") && text != ANDROID_VIEW))
  }

  private fun reportCastNeeded(context: JavaContext, node: UCallExpression, targetType: PsiType) {
    val location = context.getLocation(node)
    val message =
      "Add explicit cast to `${targetType.presentableText}`: the generic `findViewById` return value " +
      "may require a cast to compile with Java 8."
    context.report(ISSUE, node, location, message)
  }

  companion object {
    private const val ANDROID_VIEW = "android.view.View"
    private const val ON_CLICK = "onClick"

    @JvmField
    val ISSUE = Issue.create(
      id = "FindViewByIdCast",
      briefDescription = "Add Explicit Cast",
      explanation =
        """
                In Android O, the `findViewById` signature switched to using generics, which means that most of the time you can leave out explicit casts. However, due to language changes between Java 7 and 8, some call sites may no longer compile without an explicit cast. This check looks for those scenarios and suggests adding the cast now.
            """,
      category = Category.CORRECTNESS,
      priority = 4,
      severity = Severity.WARNING,
      implementation = Implementation(
        ViewTypeDetector::class.java,
        Scope.JAVA_FILE_SCOPE,
        Scope.RESOURCE_FILE_SCOPE,
      ),
    )
  }
}