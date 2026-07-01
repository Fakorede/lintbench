package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class ForegroundServicePermissionDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

  override fun getApplicableElements(): Collection<String> = listOf(TAG_SERVICE)

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    if (!context.file.name.endsWith("AndroidManifest.xml")) {
      return
    }

    if (context.project.targetSdk < TARGET_SDK_THRESHOLD) {
      return
    }

    val attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE) ?: return
    val types = attr.value.split("|").map { it.trim() }.filter { it.isNotBlank() }
    if (types.isEmpty()) {
      return
    }

    val declaredPermissions = collectDeclaredPermissions(context.document.documentElement)
    val missing = types.filter { type ->
      val required = REQUIRED_PERMISSIONS[type]
      required == null || !declaredPermissions.containsAll(required)
    }

    if (missing.isNotEmpty()) {
      val missingPermissions = missing.flatMap { type ->
        REQUIRED_PERMISSIONS[type] ?: emptyList()
      }.filter { it !in declaredPermissions }.toSet()

      val message = buildString {
        append("Missing permissions required by foregroundServiceType")
        if (missing.size > 1) append("s")
        append(": ")
        append(missingPermissions.joinToString(", "))
        append(". For targetSdkVersion ")
        append(TARGET_SDK_THRESHOLD)
        append("+, declare the corresponding FOREGROUND_SERVICE_* permissions in the manifest.")
      }

      context.report(
        ISSUE,
        attr,
        context.getValueLocation(attr),
        message
      )
    }
  }

  private fun collectDeclaredPermissions(root: org.w3c.dom.Element?): Set<String> {
    val permissions = mutableSetOf<String>()
    if (root == null) {
      return permissions
    }

    val nodeList = root.getElementsByTagName(TAG_USES_PERMISSION)
    for (i in 0 until nodeList.length) {
      val element = nodeList.item(i) as? org.w3c.dom.Element ?: continue
      val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
      if (name.isNotBlank()) {
        permissions.add(name)
      }
    }

    return permissions
  }

  companion object {
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val TAG_SERVICE = "service"
    private const val TAG_USES_PERMISSION = "uses-permission"
    private const val ATTR_NAME = "name"
    private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"
    private const val TARGET_SDK_THRESHOLD = 34

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
      "shortService" to listOf("android.permission.FOREGROUND_SERVICE_SHORT_SERVICE"),
      "specialUse" to listOf("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"),
      "mediaProcessing" to listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING"),
      "remoteMessaging" to listOf("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING")
    )

    @JvmField
    val ISSUE = Issue.create(
      id = "ForegroundServicePermission",
      briefDescription = "Missing permissions required by foregroundServiceType",
      explanation = "For targetSdkVersion 34 and higher, each `foregroundServiceType` declared on a `<service>` element requires the corresponding `FOREGROUND_SERVICE_*` permission to also be declared in the manifest. If the permission is missing, a `SecurityException` will be thrown when the foreground service is started with that type.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(ForegroundServicePermissionDetector::class.java, Scope.MANIFEST_SCOPE),
      androidSpecific = true
    )
  }
}