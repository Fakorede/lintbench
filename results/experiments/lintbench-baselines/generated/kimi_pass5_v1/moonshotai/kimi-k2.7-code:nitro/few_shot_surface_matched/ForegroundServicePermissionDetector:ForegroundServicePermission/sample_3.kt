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
import org.w3c.dom.NodeList

class ForegroundServicePermissionDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf(TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.project.targetSdkVersion < 34) {
            return
        }

        val attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE) ?: return
        val rawTypes = attr.value
        if (rawTypes.isBlank()) {
            return
        }

        val types = rawTypes.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (types.isEmpty()) {
            return
        }

        val declared = getDeclaredPermissions(context)
        val missing = mutableListOf<String>()

        for (type in types) {
            val groups = REQUIRED_PERMISSIONS[type] ?: continue
            for (group in groups) {
                if (group.none { it in declared }) {
                    missing.add(
                        if (group.size == 1) group[0] else "one of ${group.joinToString(", ")}"
                    )
                }
            }
        }

        if (missing.isNotEmpty()) {
            val message =
                "Missing permissions required by foregroundServiceType: ${missing.joinToString("; ")}. " +
                "Starting a foreground service with this type on Android 14+ will throw a SecurityException."
            context.report(ISSUE, attr, context.getValueLocation(attr), message)
        }
    }

    private fun getDeclaredPermissions(context: XmlContext): Set<String> {
        val permissions = mutableSetOf<String>()
        val nodes: NodeList = context.document.getElementsByTagName(TAG_USES_PERMISSION)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            val name = node.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name.isNotEmpty()) {
                permissions.add(name)
            }
        }
        return permissions
    }

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val ATTR_NAME = "name"
        private const val TAG_SERVICE = "service"
        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"
        private const val FOREGROUND_SERVICE = "android.permission.FOREGROUND_SERVICE"

        private fun req(vararg permissions: String): List<String> = permissions.toList()

        private val REQUIRED_PERMISSIONS = mapOf(
            "camera" to listOf(
                req(FOREGROUND_SERVICE),
                req("android.permission.FOREGROUND_SERVICE_CAMERA"),
                req("android.permission.CAMERA")
            ),
            "connectedDevice" to listOf(
                req(FOREGROUND_SERVICE),
                req("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"),
                req(
                    "android.permission.BLUETOOTH_CONNECT",
                    "android.permission.BLUETOOTH_SCAN",
                    "android.permission.UWB_RANGING"
                )
            ),
            "dataSync" to listOf(
                req(FOREGROUND_SERVICE),
                req("android.permission.FOREGROUND_SERVICE_DATA_SYNC")
            ),
            "health" to listOf(
                req(FOREGROUND_SERVICE),
                req("android.permission.FOREGROUND_SERVICE_HEALTH"),
                req(
                    "android.permission.ACTIVITY_RECOGNITION",
                    "android.permission.BODY_SENSORS",
                    "android.permission.HIGH_SAMPLING_RATE_SENSORS"
                )
            ),
            "location" to listOf(
                req(FOREGROUND_SERVICE),
                req("android.permission.FOREGROUND_SERVICE_LOCATION"),
                req(
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.ACCESS_COARSE_LOCATION"
                )
            ),
            "mediaPlayback" to listOf(
                req(FOREGROUND_SERVICE),
                req("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK")
            ),
            "mediaProcessing" to listOf(
                req(FOREGROUND_SERVICE),
                req("android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING")
            ),
            "mediaProjection" to listOf(
                req(FOREGROUND_SERVICE),
                req("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION")
            ),
            "microphone" to listOf(
                req(FOREGROUND_SERVICE),
                req("android.permission.FOREGROUND_SERVICE_MICROPHONE"),
                req("android.permission.RECORD_AUDIO")
            ),
            "phoneCall" to listOf(
                req(FOREGROUND_SERVICE),
                req("android.permission.FOREGROUND_SERVICE_PHONE_CALL"),
                req("android.permission.READ_PHONE_STATE")
            ),
            "remoteMessaging" to listOf(
                req(FOREGROUND_SERVICE),
                req("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING")
            ),
            "shortService" to listOf(
                req("android.permission.FOREGROUND_SERVICE_SHORT_SERVICE")
            ),
            "specialUse" to listOf(
                req(FOREGROUND_SERVICE),
                req("android.permission.FOREGROUND_SERVICE_SPECIAL_USE")
            ),
            "systemExempted" to listOf(
                req(FOREGROUND_SERVICE),
                req("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED")
            )
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = "On Android 14 (API 34) and higher, every foreground service type " +
                "declared in a <service> element requires a matching permission to be declared " +
                "in the manifest. If the required permission is missing, a SecurityException is " +
                "thrown when the service is started as a foreground service.",
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