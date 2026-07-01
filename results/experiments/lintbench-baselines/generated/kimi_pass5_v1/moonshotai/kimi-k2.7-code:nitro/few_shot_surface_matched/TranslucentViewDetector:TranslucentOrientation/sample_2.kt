package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AndroidVersion
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceFolderType
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.w3c.dom.Attr
import org.w3c.dom.Element

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

class TranslucentViewDetector : Detector(), SourceCodeScanner, XmlScanner {

  private val translucentThemes = mutableSetOf<String>()
  private val translucentActivityClasses = mutableSetOf<String>()
  private val activityAttributes = mutableMapOf<Element, Pair<Attr?, Attr?>>()

  override fun getApplicableAttributes(): Collection<String>? {
    return listOf("theme", "screenOrientation")
  }

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    val element = attribute.ownerElement ?: return
    if (element.tagName != "activity") return

    val localName = attribute.localName ?: return
    val current = activityAttributes[element] ?: Pair(null, null)
    activityAttributes[element] = when (localName) {
      "theme" -> attribute to current.second
      "screenOrientation" -> current.first to attribute
      else -> current
    }
  }

  override fun getApplicableElements(): Collection<String>? {
    return listOf("activity", "style")
  }

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.VALUES
  }

  override fun visitElement(context: XmlContext, element: Element) {
    when (element.tagName) {
      "style" -> {
        val styleName = element.getAttribute("name")
        if (styleName.isBlank()) return

        val parent = element.getAttribute("parent")
        val translucent = styleName.contains("translucent", ignoreCase = true) ||
          parent.contains("translucent", ignoreCase = true) ||
          hasWindowIsTranslucentItem(element)

        if (translucent) {
          translucentThemes.add(styleName)
        }
      }
      "activity" -> {
        val (themeAttr, orientationAttr) = activityAttributes[element] ?: run {
          val theme = element.getAttributeNodeNS(ANDROID_URI, "theme")
          val orientation = element.getAttributeNodeNS(ANDROID_URI, "screenOrientation")
          theme to orientation
        }

        val themeValue = themeAttr?.value ?: return
        val orientationValue = orientationAttr?.value ?: return

        if (isThemeTranslucent(themeValue) && isFixedOrientation(orientationValue)) {
          context.report(
            ISSUE,
            element,
            context.getElementLocation(element),
            "Mixing screen orientation and translucency is not supported on apps targeting O or higher"
          )
        }
      }
    }
  }

  override fun getApplicableMethodNames(): List<String>? {
    return listOf("setRequestedOrientation", "setTheme")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.app.Activity")) return

    val containingClass = node.getParentOfType(UClass::class.java, true) ?: return
    val className = containingClass.qualifiedName ?: return

    when (method.name) {
      "setTheme" -> {
        val argument = node.valueArguments.firstOrNull() ?: return
        val argumentText = argument.sourcePsi?.text ?: return
        if (argumentText.contains("translucent", ignoreCase = true) ||
          argumentText.contains("windowIsTranslucent", ignoreCase = true)) {
          translucentActivityClasses.add(className)
        }
      }
      "setRequestedOrientation" -> {
        val argument = node.valueArguments.firstOrNull() ?: return
        val argumentText = argument.sourcePsi?.text ?: return
        if (argumentText.contains("UNSPECIFIED", ignoreCase = true)) return

        if (className.contains("translucent", ignoreCase = true) ||
          className in translucentActivityClasses) {
          context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Mixing screen orientation and translucency is not supported on apps targeting O or higher"
          )
        }
      }
    }
  }

  override fun filterIncident(context: Context, incident: Incident): Boolean {
    val targetSdk = context.project.targetSdkVersion
    return targetSdk == null || targetSdk.apiLevel >= ANDROID_O
  }

  companion object {
    private const val ANDROID_O = 26

    @JvmField
    val ISSUE = Issue.create(
      id = "TranslucentOrientation",
      briefDescription = "Mixing screen orientation and translucency",
      explanation = """
        Specifying a fixed screen orientation with a translucent theme isn't supported
        on apps with targetSdkVersion O or greater, since there can be another activity
        visible behind your activity with a conflicting orientation request. On devices
        running O or greater this state can throw an exception.
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(TranslucentViewDetector::class.java, Scope.ALL)
    )

    private fun isFixedOrientation(value: String): Boolean {
      return value.isNotBlank() && !value.equals("unspecified", ignoreCase = true)
    }

    private fun isThemeTranslucent(value: String): Boolean {
      val name = value.substringAfterLast('/').removePrefix("@")
      return name.contains("translucent", ignoreCase = true)
    }

    private fun hasWindowIsTranslucentItem(element: Element): Boolean {
      var child = element.firstChild
      while (child != null) {
        if (child is Element && child.tagName == "item") {
          val itemName = child.getAttribute("name")
          val itemValue = child.textContent?.trim()
          if ((itemName == "android:windowIsTranslucent" || itemName == "windowIsTranslucent") &&
            itemValue == "true") {
            return true
          }
        }
        child = child.nextSibling
      }
      return false
    }
  }
}