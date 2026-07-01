package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UTypeCastExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.w3c.dom.Attr

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

  private val idToView = mutableMapOf<String, String>()

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.LAYOUT
  }

  override fun getApplicableAttributes(): Collection<String> {
    return listOf(ATTR_ID)
  }

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    if (attribute.namespaceURI != ANDROID_URI) {
      return
    }
    val value = attribute.value ?: return
    val idName = extractIdName(value) ?: return
    val viewType = attribute.ownerElement?.tagName ?: return
    idToView[idName] = viewType
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf("findViewById")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.app.Activity") &&
        !context.evaluator.isMemberInSubClassOf(method, "android.view.View")) {
      return
    }

    if (node.uastParent is UTypeCastExpression) {
      return
    }

    val parent = node.uastParent
    if (parent is UVariable) {
      return
    }
    if (parent is UBinaryExpression &&
        parent.operator == UastBinaryOperator.ASSIGN &&
        parent.rightOperand == node) {
      return
    }

    val idName = getResourceIdName(node) ?: return
    val viewType = idToView[idName] ?: return
    val source = node.asSourceString()
    val location = context.getLocation(node)
    val message = "Add explicit cast to $viewType for findViewById(R.id.$idName)"
    val fix = LintFix.create()
      .name("Add explicit cast to $viewType")
      .replace()
      .range(location)
      .with("($viewType) $source")
      .build()

    context.report(ISSUE, node, location, message, fix)
  }

  private fun extractIdName(value: String): String? {
    return when {
      value.startsWith("@+id/") -> value.substring("@+id/".length)
      value.startsWith("@id/") -> value.substring("@id/".length)
      value.startsWith("@android:id/") -> value.substring("@android:id/".length)
      value.contains("/") -> value.substring(value.lastIndexOf('/') + 1)
      else -> value
    }
  }

  private fun getResourceIdName(node: UCallExpression): String? {
    val arg = node.valueArguments.firstOrNull() ?: return null
    return if (arg is UReferenceExpression) {
      arg.resolvedName ?: arg.asSourceString().substringAfterLast(".")
    } else {
      null
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "FindViewByIdCast",
      briefDescription = "Add Explicit Cast",
      explanation = """
        In Android O, the findViewById signature switched to using generics, which means
        that most of the time you can leave out explicit casts and just assign the result
        of the findViewById call to variables of specific view classes. However, due to
        language changes between Java 7 and 8, this change may cause code to not compile
        without explicit casts. This lint check looks for these scenarios and suggests
        casts to be added now such that the code will continue to compile if the language
        level is updated to 1.8.
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(
        ViewTypeDetector::class.java,
        EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE),
      ),
    )
  }
}