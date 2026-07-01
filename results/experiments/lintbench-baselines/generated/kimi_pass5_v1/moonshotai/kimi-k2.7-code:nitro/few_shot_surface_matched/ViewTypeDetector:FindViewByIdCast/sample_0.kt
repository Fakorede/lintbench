package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
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
import java.util.EnumSet
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UTypeCastExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.w3c.dom.Attr

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

  private val idToViewTag = mutableMapOf<Int, String>()

  override fun appliesTo(folderType: ResourceFolderType): Boolean =
    folderType == ResourceFolderType.LAYOUT

  override fun getApplicableAttributes(): Collection<String> = listOf("id")

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    if (attribute.namespaceURI != ANDROID_URI) return

    val value = attribute.value ?: return
    val name = when {
      value.startsWith("@+id/") -> value.substring("@+id/".length)
      value.startsWith("@id/") -> value.substring("@id/".length)
      else -> return
    }
    if (name.isEmpty()) return

    val id = context.getResourceId(ResourceType.ID, name)
    if (id == 0) return

    val element = attribute.ownerElement ?: return
    val tag = element.tagName
    val viewClass = when {
      tag == "view" -> element.getAttribute("class").takeIf { it.isNotEmpty() } ?: tag
      tag == "fragment" -> element.getAttributeNS(ANDROID_URI, "name").takeIf { it.isNotEmpty() } ?: tag
      else -> tag
    }

    idToViewTag[id] = viewClass
  }

  override fun getApplicableMethodNames(): List<String> = listOf("findViewById")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.app.Activity") &&
      !context.evaluator.isMemberInSubClassOf(method, "android.view.View") &&
      !context.evaluator.isMemberInSubClassOf(method, "android.app.Fragment") &&
      !context.evaluator.isMemberInSubClassOf(method, "android.support.v4.app.Fragment")
    ) {
      return
    }

    val arg = node.valueArguments.firstOrNull() ?: return
    val id = ConstantEvaluator.evaluate(context, arg) as? Int ?: return
    val viewTag = idToViewTag[id] ?: return

    if (node.isExplicitCast()) return

    val expectedType = getExpectedType(node)
    if (expectedType != null) {
      val expectedName = expectedType.canonicalText
      if (expectedName == viewTag ||
        expectedName == "android.view.View" ||
        expectedName == "java.lang.Object"
      ) {
        return
      }
    }

    context.report(
      ISSUE,
      node,
      context.getLocation(node),
      "Add explicit cast to `$viewTag` for this `findViewById` call; without it the code may not compile with Java 8."
    )
  }

  private fun UExpression.isExplicitCast(): Boolean {
    val parent = uastParent
    return parent is UTypeCastExpression && parent.expression == this
  }

  private fun getExpectedType(node: UExpression): PsiType? {
    val parent = node.uastParent ?: return null
    return when (parent) {
      is UVariable -> parent.type
      is UBinaryExpression -> {
        if (parent.operator == UastBinaryOperator.ASSIGN) {
          parent.leftOperand.getExpressionType()
        } else null
      }
      is UCallExpression -> {
        val index = parent.valueArguments.indexOf(node)
        if (index >= 0) {
          parent.resolve()?.parameterList?.parameters?.getOrNull(index)?.type
        } else null
      }
      else -> null
    }
  }

  companion object {
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

    @JvmField
    val ISSUE = Issue.create(
      id = "FindViewByIdCast",
      briefDescription = "Add Explicit Cast",
      explanation = """
        In Android O, the `findViewById` signature switched to using generics, which means that most
        of the time you can leave out explicit casts and just assign the result of the
        `findViewById` call to variables of specific view classes.

        However, due to language changes between Java 7 and 8, this change may cause code to not
        compile without explicit casts. This lint check looks for these scenarios and suggests
        casts to be added now such that the code will continue to compile if the language level is
        updated to 1.8.
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(
        ViewTypeDetector::class.java,
        EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE)
      ),
      androidSpecific = true
    )
  }
}