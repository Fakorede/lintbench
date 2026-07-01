package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.SdkConstants
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String>? = listOf(SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.project.targetSdk < 34) {
            return
        }

        val attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
            ?: return
        val attrValue = attr.value?.takeIf { it.isNotBlank() } ?: return

        val declaredPermissions = getDeclaredPermissions(context)

        val missing = attrValue.split("|", ",")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .flatMap { FOREGROUND_SERVICE_TYPE_PERMISSIONS[it] ?: emptyList() }
            .filterNot { it in declaredPermissions }
            .toSet()

        if (missing.isNotEmpty()) {
            context.report(
                ISSUE,
                context.getLocation(attr),
                "Missing permissions required by foregroundServiceType " +
                    "for targetSdkVersion >= 34: ${missing.joinToString(", ")}"
            )
        }
    }

    private fun getDeclaredPermissions(context: XmlContext): Set<String> {
        val root = context.document.documentElement ?: return emptySet()
        val result = HashSet<String>()
        val permissions = root.getElementsByTagName(USES_PERMISSION)
        for (i in 0 until permissions.length) {
            val node = permissions.item(i) as? Element ?: continue
            val name = node.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_NAME)
                .takeIf { it.isNotBlank() } ?: continue

            result.add(name)
            if (name.startsWith(ANDROID_PERMISSION_PREFIX)) {
                result.add(name.removePrefix(ANDROID_PERMISSION_PREFIX))
            } else {
                result.add(ANDROID_PERMISSION_PREFIX + name)
            }
        }
        return result
    }

    companion object {
        private const val SERVICE = "service"
        private const val USES_PERMISSION = "uses-permission"
        private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"
        private const val ATTR_NAME = "name"
        private const val ANDROID_PERMISSION_PREFIX = "android.permission."

        private val FOREGROUND_SERVICE_TYPE_PERMISSIONS = mapOf(
            "camera" to listOf(ANDROID_PERMISSION_PREFIX + "FOREGROUND_SERVICE_CAMERA"),
            "connectedDevice" to listOf(ANDROID_PERMISSION_PREFIX + "FOREGROUND_SERVICE_CONNECTED_DEVICE"),
            "dataSync" to listOf(ANDROID_PERMISSION_PREFIX + "FOREGROUND_SERVICE_DATA_SYNC"),
            "health" to listOf(ANDROID_PERMISSION_PREFIX + "FOREGROUND_SERVICE_HEALTH"),
            "location" to listOf(ANDROID_PERMISSION_PREFIX + "FOREGROUND_SERVICE_LOCATION"),
            "mediaPlayback" to listOf(ANDROID_PERMISSION_PREFIX + "FOREGROUND_SERVICE_MEDIA_PLAYBACK"),
            "mediaProjection" to listOf(ANDROID_PERMISSION_PREFIX + "FOREGROUND_SERVICE_MEDIA_PROJECTION"),
            "microphone" to listOf(ANDROID_PERMISSION_PREFIX + "FOREGROUND_SERVICE_MICROPHONE"),
            "phoneCall" to listOf(ANDROID_PERMISSION_PREFIX + "FOREGROUND_SERVICE_PHONE_CALL"),
            "remoteMessaging" to listOf(ANDROID_PERMISSION_PREFIX + "FOREGROUND_SERVICE_REMOTE_MESSAGING"),
            "shortService" to listOf(ANDROID_PERMISSION_PREFIX + "FOREGROUND_SERVICE_SHORT_SERVICE"),
            "specialUse" to listOf(ANDROID_PERMISSION_PREFIX + "FOREGROUND_SERVICE_SPECIAL_USE"),
            "systemExempted" to listOf(ANDROID_PERMISSION_PREFIX + "FOREGROUND_SERVICE_SYSTEM_EXEMPTED")
        )

        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For apps targeting Android 14 (API 34) or higher, each
                `android:foregroundServiceType` declared in a `<service>` element
                requires a corresponding `android.permission.FOREGROUND_SERVICE_*`
                permission in the manifest. If the required permissions are missing,
                starting the foreground service with that type will throw a
                `SecurityException`.
            """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}