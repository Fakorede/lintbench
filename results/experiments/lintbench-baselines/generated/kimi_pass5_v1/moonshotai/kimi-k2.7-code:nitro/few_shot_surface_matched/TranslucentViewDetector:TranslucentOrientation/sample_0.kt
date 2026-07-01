package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.FileType
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.getParentOfType

class TranslucentViewDetector : Detector(), SourceCodeScanner, XmlScanner {

  private val activityThemes = mutableMapOf<String, String>()
  private val activityOrientations = mutableMapOf<String, String>()
  private val reportedActivities = mutableSetOf<String>()

  private val translucentClasses = mutableMapOf<String, Boolean>()
  private val fixedOrientationClasses = mutableMapOf<String, Boolean>()
  private val reportedClasses = mutableSetOf<String>()

  override fun getApplicableElements(): Collection<String> {
    return listOf("activity")
  }

  override fun getApplicableAttributes(): Collection<String> {
    return listOf("theme", "screenOrientation")
  }

  override fun appliesTo(context: Context): Boolean {
    return true
  }

  override fun appliesTo(fileType: FileType): Boolean {
    return fileType == FileType.XML
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    if (element.tagName != "activity") return
    val name = element.getAttributeNS(ANDROID_URI, "name")
    if (name.isBlank()) return
    checkActivity(context, name, element)
  }

  override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
    val element = attribute.ownerElement ?: return
    if (element.tagName != "activity") return
    val name = element.getAttributeNS(ANDROID_URI, "name")
    if (name.isBlank()) return
    when (attribute.localName) {
      "theme" -> activityThemes[name] = attribute.value
      "screenOrientation" -> activityOrientations[name] = attribute.value
    }
    checkActivity(context, name, element)
  }

  private fun checkActivity(
    context: XmlContext,
    activityName: String,
    element: org.w3c.dom.Element
  ) {
    val theme = activityThemes[activityName] ?: element.getAttributeNS(ANDROID_URI, "theme")
    val orientation =
      activityOrientations[activityName] ?: element.getAttributeNS(ANDROID_URI, "screenOrientation")
    if (theme.isBlank() || orientation.isBlank()) return
    if (reportedActivities.contains(activityName)) return

    if (isTranslucentTheme(theme) && isFixedOrientation(orientation)) {
      reportedActivities.add(activityName)
      val attr = element.getAttributeNodeNS(ANDROID_URI, "screenOrientation") ?: return
      context.report(
        ISSUE,
        attr,
        context.getValueLocation(attr),
        "Using a fixed `android:screenOrientation` with a translucent theme is not supported " +
          "on apps with `targetSdkVersion` O or greater. Either remove the fixed orientation " +
          "or use a non-translucent theme."
      )
    }
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf("setTheme", "setRequestedOrientation")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val containingClass = node.getParentOfType(UClass::class.java, true) ?: return
    val className = containingClass.qualifiedName ?: return
    if (!context.evaluator.extendsClass(containingClass, "android.app.Activity", false)) return

    when (method.name) {
      "setTheme" -> {
        val arg = node.valueArguments.firstOrNull() ?: return
        val text = arg.asSourceString()
        if (text.contains("translucent", ignoreCase = true) ||
            text.contains("transparent", ignoreCase = true)) {
          translucentClasses[className] = true
        }
      }
      "setRequestedOrientation" -> {
        val arg = node.valueArguments.firstOrNull() ?: return
        val text = arg.asSourceString()
        if (!text.endsWith("UNSPECIFIED") && !text.endsWith("BEHIND")) {
          fixedOrientationClasses[className] = true
        }
      }
    }

    if (translucentClasses[className] == true && fixedOrientationClasses[className] == true) {
      if (reportedClasses.add(className)) {
        context.report(
          ISSUE,
          node,
          context.getLocation(node),
          "Calling `setRequestedOrientation` together with a translucent `setTheme` is not " +
            "supported on apps with `targetSdkVersion` O or greater."
        )
      }
    }
  }

  override fun filterIncident(context: Context, incident: Incident): Boolean {
    return context.project.targetSdkVersion.featureLevel >= 26
  }

  companion object {
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val ANDROID_O = 26

    private val NON_FIXED_ORIENTATIONS = setOf("unspecified", "behind", "sensor", "fullSensor", "fullUser")

    @JvmField
    val ISSUE = Issue.create(
      id = "TranslucentOrientation",
      briefDescription = "Mixing screenOrientation and translucency",
      explanation = """
        Specifying a fixed screen orientation with a translucent theme is not supported
        on apps with `targetSdkVersion` O or greater since another visible activity may have
        a conflicting orientation request. Devices running Android O or later will throw an
        exception if this state is detected. Either remove the fixed orientation or use a
        non-translucent theme.
      """,
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(
        TranslucentViewDetector::class.java,
        Scope.MANIFEST_SCOPE,
        Scope.JAVA_FILE_SCOPE
      )
    )

    private fun isTranslucentTheme(theme: String): Boolean {
      return theme.contains("translucent", ignoreCase = true) ||
        theme.contains("transparent", ignoreCase = true) ||
        theme.contains("windowIsTranslucent", ignoreCase = true)
    }

    private fun isFixedOrientation(orientation: String): Boolean {
      return orientation.lowercase() !in NON_FIXED_ORIENTATIONS
    }
  }
}