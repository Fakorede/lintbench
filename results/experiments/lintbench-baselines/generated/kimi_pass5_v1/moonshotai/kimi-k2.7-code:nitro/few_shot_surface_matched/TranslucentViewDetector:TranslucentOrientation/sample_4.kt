package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.getParentOfType

class TranslucentViewDetector : Detector(), SourceCodeScanner, XmlScanner {

  companion object {
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val TAG_ACTIVITY = "activity"
    private const val ATTR_NAME = "name"
    private const val ATTR_THEME = "theme"
    private const val ATTR_SCREEN_ORIENTATION = "screenOrientation"
    private const val METHOD_SET_REQUESTED_ORIENTATION = "setRequestedOrientation"
    private const val CLASS_ACTIVITY = "android.app.Activity"
    private const val O = 26

    private const val MESSAGE =
      "Specifying a fixed screen orientation with a translucent theme is not supported on apps with targetSdkVersion O or higher."

    @JvmField
    val ISSUE = Issue.create(
      id = "TranslucentOrientation",
      briefDescription = "Mixing screen orientation and translucency",
      explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported on apps with
                `targetSdkVersion` O or greater, since there can be another activity visible behind your activity
                with a conflicting orientation request. Devices running platform version O or greater will throw
                an exception if this state is detected.
            """,
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(
        TranslucentViewDetector::class.java,
        Scope.MANIFEST_SCOPE,
        Scope.JAVA_FILE_SCOPE
      ),
    )
  }

  private val translucentActivities = mutableSetOf<String>()

  override fun getApplicableAttributes(): Collection<String> =
    listOf(ATTR_SCREEN_ORIENTATION)

  override fun getApplicableElements(): Collection<String> =
    listOf(TAG_ACTIVITY)

  override fun appliesTo(context: XmlContext): Boolean =
    context.project.isManifestFile(context.file)

  override fun appliesTo(context: JavaContext): Boolean = true

  override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
    val owner = attribute.ownerElement ?: return
    if (owner.tagName != TAG_ACTIVITY) return

    val orientation = attribute.value ?: return
    if (!isFixedOrientation(orientation)) return

    val theme = owner.getAttributeNS(ANDROID_URI, ATTR_THEME)
    if (theme.isBlank() || !isTranslucentTheme(theme)) return

    val location = context.getValueLocation(attribute)
    context.report(
      Incident(ISSUE, attribute, location, MESSAGE),
      targetSdkAtLeast(O)
    )
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    if (element.tagName != TAG_ACTIVITY) return

    val theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
    if (theme.isNotBlank() && isTranslucentTheme(theme)) {
      getActivityClassName(element, context)?.let { translucentActivities.add(it) }
    }
  }

  override fun getApplicableMethodNames(): List<String> =
    listOf(METHOD_SET_REQUESTED_ORIENTATION)

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val containingClass = node.getParentOfType(UClass::class.java) ?: return
    if (!context.evaluator.isSubClassOf(containingClass, CLASS_ACTIVITY)) return

    val className = containingClass.qualifiedName ?: return
    if (className !in translucentActivities) return

    val location = context.getLocation(node)
    context.report(
      Incident(ISSUE, node, location, MESSAGE),
      targetSdkAtLeast(O)
    )
  }

  override fun filterIncident(context: Context, incident: Incident, scope: Any?): Boolean = true

  private fun isFixedOrientation(orientation: String): Boolean {
    return orientation.isNotBlank() &&
      orientation != "unspecified" &&
      orientation != "sensor"
  }

  private fun isTranslucentTheme(theme: String): Boolean {
    return theme.contains("Translucent", ignoreCase = true)
  }

  private fun getActivityClassName(element: org.w3c.dom.Element, context: XmlContext): String? {
    val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME).takeIf { it.isNotBlank() } ?: return null
    val pkg = context.mainProject.getPackage() ?: return name
    return when {
      name.startsWith(".") -> pkg + name
      !name.contains(".") -> "$pkg.$name"
      else -> name
    }
  }
}