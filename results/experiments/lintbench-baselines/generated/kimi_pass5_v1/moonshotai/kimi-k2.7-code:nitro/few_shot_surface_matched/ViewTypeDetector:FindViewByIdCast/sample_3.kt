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
import org.jetbrains.uast.UCallExpression

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

  private val idToViewClass = mutableMapOf<String, String>()

  override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
    return folderType == com.android.resources.ResourceFolderType.LAYOUT
  }

  override fun getApplicableAttributes(): Collection<String> {
    return listOf("id")
  }

  override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
    if (attribute.namespaceURI != "http://schemas.android.com/apk/res/android" ||
      attribute.localName != "id"
    ) {
      return
    }

    val idValue = attribute.value ?: return
    val idName = idValue.substringAfterLast("/")
    if (idName.isEmpty()) {
      return
    }

    val element = attribute.ownerElement ?: return
    val viewClass = when (element.tagName) {
      "view" -> element.getAttribute("class")
      else -> element.tagName
    }

    if (viewClass.isNotEmpty()) {
      idToViewClass[idName] = viewClass
    }
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf("findViewById")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.view.View") &&
      !context.evaluator.isMemberInSubClassOf(method, "android.app.Activity") &&
      !context.evaluator.isMemberInSubClassOf(method, "android.app.Fragment")
    ) {
      return
    }

    val argument = node.valueArguments.firstOrNull() ?: return
    val idName = getResourceIdName(argument) ?: return
    val viewClass = idToViewClass[idName] ?: return

    if (isAlreadyCast(node)) {
      return
    }

    val expectedType = getExpectedType(node) ?: return
    if (expectedType == "android.view.View") {
      return
    }

    val expectedClass = context.evaluator.findClass(expectedType) ?: return
    if (!context.evaluator.extendsClass(expectedClass, "android.view.View", false)) {
      return
    }

    context.report(
      ISSUE,
      node,
      context.getLocation(node),
      "Add explicit cast to $viewClass"
    )
  }

  private fun getResourceIdName(argument: org.jetbrains.uast.UExpression): String? {
    val expression = unwrapParenthesized(argument)
    return when (expression) {
      is org.jetbrains.uast.UQualifiedReferenceExpression -> {
        val selector = expression.selector
        val receiver = expression.receiver
        if (receiver is org.jetbrains.uast.UQualifiedReferenceExpression &&
          receiver.selector.asSourceString() == "id" &&
          receiver.receiver.asSourceString() == "R"
        ) {
          selector.asSourceString()
        } else {
          null
        }
      }

      is org.jetbrains.uast.USimpleNameReferenceExpression -> expression.identifier
      else -> null
    }
  }

  private fun unwrapParenthesized(expression: org.jetbrains.uast.UExpression): org.jetbrains.uast.UExpression {
    var current = expression
    while (current is org.jetbrains.uast.UParenthesizedExpression) {
      current = current.expression
    }
    return current
  }

  private fun isAlreadyCast(node: UCallExpression): Boolean {
    var current: org.jetbrains.uast.UElement = node
    var parent = node.uastParent
    while (parent is org.jetbrains.uast.UParenthesizedExpression) {
      current = parent
      parent = parent.uastParent
    }
    return parent is org.jetbrains.uast.UCastExpression && parent.operand == current
  }

  private fun getExpectedType(node: UCallExpression): String? {
    var current: org.jetbrains.uast.UElement = node
    var parent = node.uastParent
    while (parent is org.jetbrains.uast.UParenthesizedExpression) {
      current = parent
      parent = parent.uastParent
    }

    return when (parent) {
      is org.jetbrains.uast.UCastExpression -> null
      is org.jetbrains.uast.UVariable -> {
        if (parent.uastInitializer == current) parent.type.canonicalText else null
      }

      is org.jetbrains.uast.UBinaryExpression -> {
        if (parent.operator == org.jetbrains.uast.UastBinaryOperator.ASSIGN &&
          parent.rightOperand == current
        ) {
          parent.leftOperand.getExpressionType()?.canonicalText
        } else {
          null
        }
      }

      is