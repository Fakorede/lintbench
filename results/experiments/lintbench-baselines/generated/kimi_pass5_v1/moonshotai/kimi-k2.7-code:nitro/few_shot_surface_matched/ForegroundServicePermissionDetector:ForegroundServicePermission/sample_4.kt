package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class ForegroundServicePermissionDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

  override fun getApplicableElements(): Collection<String> = listOf("service")

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    if (context.project.targetSdk < ANDROID_14) {
      return
    }

    val attr = element.getAttributeNodeNS(ANDROID_URI, "foregroundServiceType") ?: return
    val typeString = attr.value ?: return
    if (typeString.isBlank()) {
      return
    }

    val declaredPermissions = getDeclaredPermissions(context)
    val types = typeString.split("|").map { it.trim() }.filter { it.isNotEmpty() }

    for (type in types) {
      val required = FOREGROUND_SERVICE_PERMISSIONS[type] ?: continue
      val missing = required.filter { it !in declaredPermissions }
      if (missing.isNotEmpty()) {
        val message =
          "Missing permission(s) ${missing.joinToString()} required by foregroundServiceType `$type`"
        context.report(ISSUE, attr, context.getValueLocation(attr), message)
      }
    }
  }

  private fun getDeclaredPermissions(context: XmlContext): Set<String> {
    val root = context.document.documentElement ?: return emptySet()
    val result = mutableSetOf<String>()
    val nodeList = root.getElementsByTagName("uses-permission")
    for (i in 0 until nodeList.length) {
      val node = nodeList.item(i) as? org.w3c.dom.Element ?: continue
      val name = node.getAttributeNS(ANDROID_URI, "name")
      if (name.isNotEmpty()) {
        result.add(name)
      }
    }
    return result
  }

  companion object {
    private const val ANDROID_14 = 34
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

    @JvmField
    val ISSUE = Issue.create(
      id = "ForegroundServicePermission",
      briefDescription = "Missing permission required by foregroundServiceType",
      explanation =
        "For targetSdkVersion 34 and above, each foregroundServiceType listed in the " +
          "<service> element requires a specific permission to be declared in the manifest. If " +
          "the permission is missing, a SecurityException will be thrown when the foreground " +
          "service is started.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(
        ForegroundServicePermissionDetector::class.java,
        Scope.MANIFEST_SCOPE,
        Scope.JAVA_FILE_SCOPE,
      ),
    )

    private val FOREGROUND_SERVICE_PERMISSIONS: Map<String, List<String>> = mapOf(
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
      "systemExempted" to listOf("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"),
    )
  }
}