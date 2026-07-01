package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class ForegroundServicePermissionDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

  override fun getApplicableMethodNames(): List<String> = emptyList()

  override fun getApplicableElements(): List<String> = listOf(TAG_SERVICE)

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    if (context.mainProject.targetSdk < 34) {
      return
    }

    val attr = element.getAttributeNodeNS(ANDROID_NS, ATTR_FOREGROUND_SERVICE_TYPE) ?: return
    val typeString = attr.value ?: return
    if (typeString.isBlank()) {
      return
    }

    val declaredPermissions = getDeclaredPermissions(context)
    val missing = mutableListOf<String>()

    for (type in typeString.split("|")) {
      val trimmed = type.trim()
      if (trimmed.isEmpty()) {
        continue
      }
      val required = REQUIRED_PERMISSIONS[trimmed] ?: continue
      for (permission in required) {
        if (permission !in declaredPermissions) {
          missing.add(permission)
        }
      }
    }

    if (missing.isNotEmpty()) {
      val message = missing.joinToString(
        prefix = "Missing permissions required by the declared foregroundServiceType: ",
        postfix = ". These permissions must be declared in the manifest for targetSdkVersion 34+."
      )
      context.report(ISSUE, attr, context.getValueLocation(attr), message)
    }
  }

  private fun getDeclaredPermissions(context: XmlContext): Set<String> {
    val permissions = mutableSetOf<String>()
    val manifest = context.document.documentElement ?: return permissions
    val nodes = manifest.getElementsByTagName(TAG_USES_PERMISSION)
    for (i in 0 until nodes.length) {
      val node = nodes.item(i) as? org.w3c.dom.Element ?: continue
      val name = node.getAttributeNS(ANDROID_NS, ATTR_NAME)
      if (name.isNotBlank()) {
        permissions.add(name)
      }
    }
    return permissions
  }

  companion object {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val TAG_SERVICE = "service"
    private const val TAG_USES_PERMISSION = "uses-permission"
    private const val ATTR_NAME = "name"
    private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"

    private val REQUIRED_PERMISSIONS: Map<String, List<String>> = mapOf(
      "camera" to listOf("android.permission.FOREGROUND_SERVICE_CAMERA"),
      "connectedDevice" to listOf("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"),
      "dataSync" to listOf("android.permission.FOREGROUND_SERVICE_DATA_SYNC"),
      "health" to listOf("android.permission.FOREGROUND_SERVICE_HEALTH"),
      "location" to listOf("android.permission.FOREGROUND_SERVICE_LOCATION"),
      "mediaPlayback" to listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"),
      "mediaProjection" to listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"),
      "microphone" to listOf("android.permission.FOREGROUND_SERVICE_MICROPHONE"),
      "phoneCall" to listOf("android.permission.FOREGROUND_SERVICE_PHONE_CALL"),
      "remoteMessaging" to listOf("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING"),
      "shortService" to listOf("android.permission.FOREGROUND_SERVICE_SHORT_SERVICE"),
      "specialUse" to listOf("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"),
      "systemExempted" to listOf("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED")
    )

    @JvmField
    val ISSUE = Issue.create(
      id = "ForegroundServicePermission",
      briefDescription = "Missing permissions required by foregroundServiceType",
      explanation = "For targetSdkVersion >= 34, each `foregroundServiceType` listed in the `<service>` element requires a corresponding `android.permission.FOREGROUND_SERVICE_*` permission to be declared in the manifest. If a required permission is missing, a `SecurityException` will be thrown when the foreground service is started with that type.",
      category = Category.CORRECTNESS,
      priority = 8,
      severity = Severity.ERROR,
      implementation = Implementation(
        ForegroundServicePermissionDetector::class.java,
        Scope.MANIFEST_SCOPE
      ),
      androidSpecific = true
    )
  }
}