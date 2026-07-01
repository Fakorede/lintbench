package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_FOREGROUND_SERVICE_TYPE
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_PERMISSION
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
    val targetSdk = context.targetSdkVersion ?: return
    if (targetSdk < 34) return

    val attribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE) ?: return
    val typeValue = attribute.value
    if (typeValue.isEmpty() || typeValue.startsWith("@")) return

    val types = typeValue.split("|").map { it.trim() }
    val manifest = context.manifest ?: return
    val permissionNodes = manifest.getElementsByTagName(TAG_USES_PERMISSION)
    val declaredPermissions = mutableSetOf<String>()
    for (i in 0 until permissionNodes.length) {
      val permElement = permissionNodes.item(i) as? Element ?: continue
      val permName = permElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
      if (permName.isNotEmpty()) {
        declaredPermissions.add(permName)
      }
    }

    val missingPermissions = mutableListOf<String>()
    for (type in types) {
      val requiredPerm = TYPE_TO_PERMISSION[type] ?: continue
      if (!declaredPermissions.contains(requiredPerm)) {
        missingPermissions.add(requiredPerm)
      }
    }

    if (missingPermissions.isNotEmpty()) {
      val message = "Missing required permission(s) for foregroundServiceType: ${missingPermissions.joinToString(", ")}. " +
        "Starting this foreground service will throw a SecurityException on Android 14+."
      context.report(
        ISSUE,
        attribute,
        context.getValueLocation(attribute),
        message
      )
    }
  }

  companion object {
    private val TYPE_TO_PERMISSION = mapOf(
      "camera" to "android.permission.FOREGROUND_SERVICE_CAMERA",
      "connectedDevice" to "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
      "dataSync" to "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
      "health" to "android.permission.FOREGROUND_SERVICE_HEALTH",
      "location" to "android.permission.FOREGROUND_SERVICE_LOCATION",
      "mediaPlayback" to "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
      "mediaProjection" to "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
      "microphone" to "android.permission.FOREGROUND_SERVICE_MICROPHONE",
      "phoneCall" to "android.permission.FOREGROUND_SERVICE_PHONE_CALL",
      "remoteMessaging" to "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING"
    )

    @JvmField
    val ISSUE = Issue.create(
      id = "ForegroundServicePermission",
      briefDescription = "Missing permissions required by foregroundServiceType",
      explanation = "For targetSdkVersion >= 34, each foregroundServiceType listed in the <service> element " +
        "requires specific sets of permissions to be declared in the manifest. If permissions are " +
        "missing, then when the foreground service is started with a foregroundServiceType that has " +
        "missing permissions, a SecurityException will be thrown.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(ForegroundServicePermissionDetector::class.java, Scope.MANIFEST_SCOPE)
    )
  }
}