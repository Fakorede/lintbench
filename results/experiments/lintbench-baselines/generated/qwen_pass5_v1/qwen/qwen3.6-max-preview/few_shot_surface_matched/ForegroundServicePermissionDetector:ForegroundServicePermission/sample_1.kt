package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.targetSdkAtLeast
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf("service")
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val ns = "http://schemas.android.com/apk/res/android"
    val typeAttr = element.getAttributeNodeNS(ns, "foregroundServiceType") ?: return
    val typeValue = typeAttr.value.trim()
    if (typeValue.isEmpty()) return

    val types = typeValue.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    if (types.isEmpty()) return

    val document = element.ownerDocument
    val declaredPermissions = mutableSetOf<String>()
    val permNodes = document.getElementsByTagName("uses-permission")
    for (i in 0 until permNodes.length) {
      val permElement = permNodes.item(i) as? Element ?: continue
      val name = permElement.getAttributeNS(ns, "name")
      if (name.isNotEmpty()) {
        declaredPermissions.add(name)
      }
    }

    for (type in types) {
      if (type == "shortService") continue

      val requiredPermission = "android.permission.FOREGROUND_SERVICE_${type.uppercase()}"
      if (declaredPermissions.contains(requiredPermission)) continue

      val message = "Foreground service type '$type' requires the $requiredPermission permission to be declared in the manifest for apps targeting API 34+."
      val location = context.getValueLocation(typeAttr)

      context.report(
        Incident(ISSUE, element, location, message),
        targetSdkAtLeast(34)
      )
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "ForegroundServicePermission",
      briefDescription = "Missing permissions required by foregroundServiceType",
      explanation = "For targetSdkVersion >= 34, each foregroundServiceType listed in the <service> element requires specific permissions to be declared in the manifest. If permissions are missing, a SecurityException will be thrown when the service is started.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(ForegroundServicePermissionDetector::class.java, Scope.MANIFEST_SCOPE),
      androidSpecific = true
    )
  }
}