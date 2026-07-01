package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_SERVICE)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val targetSdk = context.mainProject.targetSdkVersion
    if (targetSdk != -1 && targetSdk < 34) return

    val typeAttr = element.getAttributeNodeNS(ANDROID_URI, "foregroundServiceType") ?: return
    val types = typeAttr.value.split("|").map { it.trim() }
    if (types.isEmpty()) return

    val declaredPermissions = getDeclaredPermissions(context)

    for (type in types) {
      val requiredPermission = getRequiredPermission(type) ?: continue
      if (!declaredPermissions.contains(requiredPermission)) {
        context.report(
          ISSUE,
          typeAttr,
          context.getValueLocation(typeAttr),
          "Missing permission $requiredPermission required for foregroundServiceType \"$type\" on targetSdkVersion 34+"
        )
      }
    }
  }

  private fun getDeclaredPermissions(context: XmlContext): Set<String> {
    val permissions = mutableSetOf<String>()
    val root = context.document.documentElement ?: return permissions
    val children = root.childNodes
    for (i in 0 until children.length) {
      val node = children.item(i)
      if (node is Element && node.tagName == "uses-permission") {
        val name = node.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (name.isNotEmpty()) {
          permissions.add(name)
        }
      }
    }
    return permissions
  }

  private fun getRequiredPermission(type: String): String? {
    return when (type) {
      "camera" -> "android.permission.CAMERA"
      "microphone" -> "android.permission.RECORD_AUDIO"
      "location" -> "android.permission.ACCESS_FINE_LOCATION"
      "health" -> "android.permission.BODY_SENSORS"
      "connectedDevice" -> "android.permission.BLUETOOTH_CONNECT"
      "nearbyWifiDevices" -> "android.permission.NEARBY_WIFI_DEVICES"
      "phoneCall" -> "android.permission.CALL_PHONE"
      else -> null
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "ForegroundServicePermission",
      briefDescription = "Missing permissions required by foregroundServiceType",
      explanation = "For targetSdkVersion >= 34, each foregroundServiceType listed in the <service> element " +
        "requires specific sets of permissions to be declared in the manifest. If permissions are " +
        "missing, a SecurityException will be thrown when the foreground service is started.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(ForegroundServicePermissionDetector::class.java, Scope.MANIFEST_SCOPE)
    )
  }
}