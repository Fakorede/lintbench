package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class ForegroundServicePermissionDetector : ResourceXmlDetector() {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val MANIFEST_TAG = "manifest"
        private const val SERVICE_TAG = "service"
        private const val USES_SDK_TAG = "uses-sdk"
        private const val USES_PERMISSION_TAG = "uses-permission"

        private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"
        private const val ATTR_NAME = "name"
        private const val ATTR_TARGET_SDK_VERSION = "targetSdkVersion"

        private val PERMISSIONS_BY_TYPE = mapOf(
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
        )

        private val IMPLEMENTATION = Implementation(
            ForegroundServicePermissionDetector::class.java,
            java.util.EnumSet.of(Scope.MANIFEST),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For apps targeting Android 14 (API 34) or higher, every foregroundServiceType declared
                on a <service> must have the corresponding FOREGROUND_SERVICE_* permission declared
                in the manifest. If the required permission is missing, the system throws a
                SecurityException when the service is started with that foregroundServiceType.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(SERVICE_TAG)

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        if (!isManifest(context) || getTargetSdk(context) < 34) {
            return
        }

        val attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE) ?: return
        val typeString = attr.value ?: return
        if (typeString.isBlank()) {
            return
        }

        val declaredPermissions = getDeclaredPermissions(context)
        val types = typeString.split("|").map { it.trim() }.filter { it.isNotEmpty() }

        for (type in types) {
            val required = PERMISSIONS_BY_TYPE[type] ?: continue
            val missing = required.filter { it !in declaredPermissions }
            if (missing.isNotEmpty()) {
                val message =
                    "Missing permission(s) required by foregroundServiceType \"$type\": ${missing.joinToString()}"
                context.report(ISSUE, attr, context.getLocation(attr), message)
            }
        }
    }

    private fun isManifest(context: XmlContext): Boolean {
        val root = context.document.documentElement ?: return false
        return root.tagName == MANIFEST_TAG
    }

    private fun getTargetSdk(context: XmlContext): Int {
        val manifest = context.document.documentElement ?: return context.project.targetSdk
        val usesSdk = manifest.getElementsByTagName(USES_SDK_TAG).item(0) as? org.w3c.dom.Element
        if (usesSdk != null) {
            val value = usesSdk.getAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION)
            if (value.isNotBlank()) {
                try {
                    return value.toInt()
                } catch (ignore: NumberFormatException) {
                    // Fall through to project target SDK.
                }
            }
        }
        return context.project.targetSdk
    }

    private fun getDeclaredPermissions(context: XmlContext): Set<String> {
        val manifest = context.document.documentElement ?: return emptySet()
        val permissions = mutableSetOf<String>()
        val nodes = manifest.getElementsByTagName(USES_PERMISSION_TAG)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? org.w3c.dom.Element ?: continue
            val name = node.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name.isNotBlank()) {
                permissions.add(name)
            }
        }
        return permissions
    }
}