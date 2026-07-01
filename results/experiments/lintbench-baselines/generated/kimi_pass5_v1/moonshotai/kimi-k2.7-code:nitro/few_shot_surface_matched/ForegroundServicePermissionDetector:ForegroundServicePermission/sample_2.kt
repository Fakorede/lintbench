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
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

  companion object {
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val TAG_SERVICE = "service"
    private const val TAG_USES_PERMISSION = "uses-permission"
    private const val ATTR_NAME = "name"
    private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"

    private const val TARGET_SDK_THRESHOLD = 34

    private val REQUIRED_PERMISSIONS = mapOf(
      "camera" to setOf("android.permission.FOREGROUND_SERVICE_CAMERA"),
      "connectedDevice" to setOf("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"),
      "dataSync" to setOf("android.permission.FOREGROUND_SERVICE_DATA_SYNC"),
      "health" to setOf("android.permission.FOREGROUND_SERVICE_HEALTH"),
      "location" to setOf("android.permission.FOREGROUND_SERVICE_LOCATION"),
      "mediaPlayback" to setOf("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"),
      "mediaProjection" to setOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"),
      "microphone" to setOf("android.permission.FOREGROUND_SERVICE_MICROPHONE"),
      "phoneCall" to setOf("android.permission.FOREGROUND_SERVICE_PHONE_CALL"),
      "remoteMessaging" to setOf("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING"),
      "shortService" to setOf("android.permission.FOREGROUND_SERVICE_SHORT_SERVICE"),
      "specialUse" to setOf("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"),
      "systemExempted" to setOf("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED")
    )

    @JvmField
    val ISSUE = Issue.create(
      id = "ForegroundServicePermission",
      briefDescription = "Missing permissions required by foregroundServiceType",
      explanation = """
                For targetSdkVersion 34 and higher, each `foregroundServiceType` declared on a
                `<service>` element requires a corresponding permission to be declared in the
                manifest. If the permission is missing, starting the foreground service with
                that type will throw a `SecurityException`.
            """,
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(
        ForegroundServicePermissionDetector::class.java,
        Scope.MANIFEST_SCOPE
      )
    )
  }

  override fun getApplicableElements(): Collection<String> = listOf(TAG_SERVICE)

  override fun visitElement(context: XmlContext, element: Element) {
    if (context.mainProject.targetSdkVersion < TARGET_SDK_THRESHOLD) {
      return
    }

    val attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE) ?: return
    val types = attr.value.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    if (types.isEmpty()) {
      return
    }

    val declaredPermissions = getDeclaredPermissions(element)
    val missing = mutableListOf<String>()

    for (type in types) {
      val required = REQUIRED_PERMISSIONS[type] ?: continue
      missing.addAll(required.filter { it !in declaredPermissions })
    }

    if (missing.isNotEmpty()) {
      val message = "Missing permissions required by foregroundServiceType: " +
        missing.joinToString(", ")
      context.report(ISSUE, attr, context.getValueLocation(attr), message)
    }
  }

  private fun getDeclaredPermissions(serviceElement: Element): Set<String> {
    val permissions = mutableSetOf<String>()
    val document = serviceElement.ownerDocument ?: return permissions
    val nodes = document.getElementsByTagName(TAG_USES_PERMISSION)
    for (i in 0 until nodes.length) {
      val node = nodes.item(i) as? Element ?: continue
      val name = node.getAttributeNS(ANDROID_URI, ATTR_NAME)
      if (name.isNotEmpty()) {
        permissions.add(name)
      }
    }
    return permissions
  }
}