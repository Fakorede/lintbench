package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UParenthesizedExpression

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

  private val idToView = HashMap<String, String>()

  override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
    return folderType == com.android.resources.ResourceFolderType.LAYOUT
  }

  override fun getApplicableAttributes(): Collection<String>? {
    return listOf("id")
  }

  override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
    if (attribute.namespaceURI == ANDROID_URI) {
      val id = attribute.value
      if (id.startsWith("@id/") || id.startsWith("@+id/")) {
        val idName = id.substringAfter('/')
        val viewClass = attribute.ownerElement.tagName
        idToView[idName] = viewClass
      }
    }
  }

  override fun getApplicableMethodNames(): Collection<String>? {
    return listOf("findViewById")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val languageLevel = context.project.javaLanguageLevel
    if (languageLevel != null && languageLevel.isAtLeast(com.intellij.pom.java.LanguageLevel.JDK_1_8)) {
      return
    }

    val parent = skipParentheses(node.uastParent)
    if (parent is UCallExpression) {
      val call = parent
      val calledMethod = call.resolve() ?: return
      val containingClass = calledMethod.containingClass ?: return
      val methodName = calledMethod.name
      val methods = containingClass.findMethodsByName(methodName, true)
      if (methods.size > 1) {
        val args = call.valueArguments
        val index = args.indexOf(node)
        if (index >= 0) {
          val paramCount = calledMethod.parameterList.parametersCount
          var hasAmbiguity = false
          for (m in methods) {
            if (m != calledMethod && m.parameterList.parametersCount == paramCount) {
              hasAmbiguity = true
              break
            }
          }
          if (hasAmbiguity) {
            val findViewArg = node.valueArguments.firstOrNull()
            var castType = "View"
            if (findViewArg != null) {
              val idString = findViewArg.asSourceString()
              val idName = idString.substringAfterLast('.')
              val viewClass = idToView[idName]
              if (viewClass != null) {
                castType = viewClass
              }
            }
            val message = "Add explicit cast to `$castType` to avoid override ambiguity in Java 8"
            context.report(ISSUE, node, context.getLocation(node), message)
          }
        }
      }
    }
  }

  private fun skipParentheses(element: UElement?): UElement? {
    var curr = element
    while (curr is UParenthesizedExpression) {
      curr = curr.uastParent
    }
    return curr
  }

  companion object {
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

    @JvmField
    val ISSUE = Issue.create(
      id = "FindViewByIdCast",
      briefDescription = "Add Explicit Cast",
      explanation = """
        In Android O, the `findViewById` signature switched to using generics, which \
        means that most of the time you can leave out explicit casts and just assign \
        the result of the `findViewById` call to variables of specific view classes.

        However, due to language changes between Java 7 and 8, this change may cause \
        code to not compile without explicit casts. This lint check looks for these \
        scenarios and suggests casts to be added now such that the code will \
        continue to compile if the language level is updated to 1.8.
      """,
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(ViewTypeDetector::class.java, Scope.JAVA_AND_RESOURCE_FILES),
    )
  }
}