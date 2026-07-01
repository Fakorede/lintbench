package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
private const val SERVICE = "service"
private const val USES_PERMISSION = "uses-permission"
private const val USES_SDK = "uses-sdk"
private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"
private const val ATTR_NAME = "name"
private const val ATTR_TARGET_SDK_VERSION = "targetSdkVersion"
private const val TARGET_SDK_THRESHOLD = 34

private val FOREGROUND_SERVICE_TYPE_PERMISSIONS = mapOf(
    "camera" to "android.permission.FOREGROUND_SERVICE_CAMERA",
    "connectedDevice" to "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
    "dataSync" to "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
    "health" to "android.permission.FOREGROUND_SERVICE_HEALTH",
    "location" to "android.permission.FOREGROUND_SERVICE_LOCATION",
    "mediaPlayback" to "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
    "mediaProjection" to "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
    "microphone" to "android.permission.FOREGROUND_SERVICE_MICROPHONE",
    "phoneCall" to "android.permission.FOREGROUND_SERVICE_PHONE_CALL",
    "remoteMessaging" to "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING",
    "shortService" to "android.permission.FOREGROUND_SERVICE",
    "specialUse" to "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "systemExempted" to "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"
)

class ForegroundServicePermissionDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): List<String> = listOf(SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != SERVICE) return

        val targetSdk = getTargetSdkVersion(context)
        if (targetSdk < TARGET_SDK_THRESHOLD) return

        val foregroundServiceTypes = element.getAttributeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
        if (foregroundServiceTypes.isBlank()) return

        val declaredPermissions = getDeclaredPermissions(context)

        val missingPermissions = foregroundServiceTypes
            .split("|")
            .map { it.trim() }
            .mapNotNull { type ->
                FOREGROUND_SERVICE_TYPE_PERMISSIONS[type]?.takeUnless { it in declaredPermissions }
            }
            .distinct()

        if (missingPermissions.isNotEmpty()) {
            val attribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
            val location = context.getLocation(element, attribute)
            val message = "Missing permissions required by foregroundServiceType: ${missingPermissions.joinToString()}"
            context.report(ISSUE, location, message)
        }
    }

    private fun getTargetSdkVersion(context: XmlContext): Int {
        val root = context.document.documentElement
        val usesSdkElements = root.getElementsByTagName(USES_SDK)
        if (usesSdkElements.length > 0) {
            val target = (usesSdkElements.item(0) as Element).getAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION)
            val value = target.toIntOrNull()
            if (value != null) return value
        }
        return context.mainProject?.targetSdkVersion?.featureLevel ?: -1
    }

    private fun getDeclaredPermissions(context: XmlContext): Set<String> {
        val root = context.document.documentElement
        val permissionElements = root.getElementsByTagName(USES_PERMISSION)
        val result = mutableSetOf<String>()
        for (i in 0 until permissionElements.length) {
            val permissionElement = permissionElements.item(i) as Element
            val name = permissionElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name.isNotBlank()) result.add(name)
        }
        return result
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For targetSdkVersion >= 34, each foregroundServiceType listed in a <service>
                element requires a corresponding permission to be declared in the manifest.
                If the required permission is missing, a SecurityException will be thrown
                when the foreground service is started with that type.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}