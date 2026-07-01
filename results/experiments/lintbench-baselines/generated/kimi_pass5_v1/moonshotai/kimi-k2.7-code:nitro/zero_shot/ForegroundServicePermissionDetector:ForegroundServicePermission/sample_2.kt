package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_FOREGROUND_SERVICE_TYPE
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_TARGET_SDK_VERSION
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.SdkConstants.TAG_USES_SDK
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.NodeList

class ForegroundServicePermissionDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf(TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        if (getTargetSdkVersion(context) < 34) {
            return
        }

        val foregroundServiceType =
            element.getAttributeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
        if (foregroundServiceType.isBlank()) {
            return
        }

        val declaredPermissions = getDeclaredPermissions(context)
        val types = foregroundServiceType.split("\\|".toRegex())
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "none" }
        if (types.isEmpty()) {
            return
        }

        val missingByType = mutableMapOf<String, List<String>>()
        for (type in types) {
            val permissionSet = REQUIRED_PERMISSIONS[type] ?: continue
            val missing = mutableListOf<String>()

            for (permission in permissionSet.required) {
                if (!declaredPermissions.contains(permission)) {
                    missing.add(permission)
                }
            }

            for (group in permissionSet.oneOf) {
                if (group.none { declaredPermissions.contains(it) }) {
                    missing.add(group.joinToString(" or "))
                }
            }

            if (missing.isNotEmpty()) {
                missingByType[type] = missing
            }
        }

        if (missingByType.isNotEmpty()) {
            val message = buildString {
                append("Missing permissions required for foregroundServiceType")
                if (missingByType.size == 1) {
                    append(" '${missingByType.keys.first()}'")
                }
                append(":")
                for ((type, missing) in missingByType) {
                    append("\n  - $type: ${missing.joinToString(", ")}")
                }
                append(
                    "\nStarting with Android 14 (API 34), a missing permission will cause a SecurityException when the service is started."
                )
            }
            context.report(ISSUE, element, context.getLocation(element), message)
        }
    }

    private fun getTargetSdkVersion(context: XmlContext): Int {
        val usesSdkNodes: NodeList = context.document.getElementsByTagName(TAG_USES_SDK)
        if (usesSdkNodes.length > 0) {
            val usesSdk = usesSdkNodes.item(0) as Element
            val targetSdk = usesSdk.getAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION)
            if (targetSdk.isNotBlank()) {
                return targetSdk.toIntOrNull() ?: -1
            }
        }
        return -1
    }

    private fun getDeclaredPermissions(context: XmlContext): Set<String> {
        val permissions = mutableSetOf<String>()
        val nodes: NodeList = context.document.getElementsByTagName(TAG_USES_PERMISSION)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            val name = node.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name.isNotBlank()) {
                permissions.add(name)
            }
        }
        return permissions
    }

    private data class PermissionSet(
        val required: List<String>,
        val oneOf: List<List<String>> = emptyList()
    )

    companion object {
        private const val PREFIX = "android.permission."

        private const val FOREGROUND_SERVICE_CAMERA = "${PREFIX}FOREGROUND_SERVICE_CAMERA"
        private const val CAMERA = "${PREFIX}CAMERA"
        private const val FOREGROUND_SERVICE_CONNECTED_DEVICE =
            "${PREFIX}FOREGROUND_SERVICE_CONNECTED_DEVICE"
        private const val FOREGROUND_SERVICE_DATA_SYNC = "${PREFIX}FOREGROUND_SERVICE_DATA_SYNC"
        private const val FOREGROUND_SERVICE_HEALTH = "${PREFIX}FOREGROUND_SERVICE_HEALTH"
        private const val FOREGROUND_SERVICE_LOCATION = "${PREFIX}FOREGROUND_SERVICE_LOCATION"
        private const val ACCESS_FINE_LOCATION = "${PREFIX}ACCESS_FINE_LOCATION"
        private const val ACCESS_COARSE_LOCATION = "${PREFIX}ACCESS_COARSE_LOCATION"
        private const val FOREGROUND_SERVICE_MEDIA_PLAYBACK =
            "${PREFIX}FOREGROUND_SERVICE_MEDIA_PLAYBACK"
        private const val FOREGROUND_SERVICE_MEDIA_PROJECTION =
            "${PREFIX}FOREGROUND_SERVICE_MEDIA_PROJECTION"
        private const val FOREGROUND_SERVICE_MICROPHONE = "${PREFIX}FOREGROUND_SERVICE_MICROPHONE"
        private const val RECORD_AUDIO = "${PREFIX}RECORD_AUDIO"
        private const val FOREGROUND_SERVICE_PHONE_CALL = "${PREFIX}FOREGROUND_SERVICE_PHONE_CALL"
        private const val MANAGE_OWN_CALLS = "${PREFIX}MANAGE_OWN_CALLS"
        private const val FOREGROUND_SERVICE_REMOTE_MESSAGING =
            "${PREFIX}FOREGROUND_SERVICE_REMOTE_MESSAGING"
        private const val FOREGROUND_SERVICE_SHORT_SERVICE =
            "${PREFIX}FOREGROUND_SERVICE_SHORT_SERVICE"
        private const val FOREGROUND_SERVICE_SPECIAL_USE = "${PREFIX}FOREGROUND_SERVICE_SPECIAL_USE"
        private const val FOREGROUND_SERVICE_SYSTEM_EXEMPTED =
            "${PREFIX}FOREGROUND_SERVICE_SYSTEM_EXEMPTED"

        private val REQUIRED_PERMISSIONS = mapOf(
            "camera" to PermissionSet(
                required = listOf(FOREGROUND_SERVICE_CAMERA, CAMERA)
            ),
            "connectedDevice" to PermissionSet(
                required = listOf(FOREGROUND_SERVICE_CONNECTED_DEVICE)
            ),
            "dataSync" to PermissionSet(
                required = listOf(FOREGROUND_SERVICE_DATA_SYNC)
            ),
            "health" to PermissionSet(
                required = listOf(FOREGROUND_SERVICE_HEALTH)
            ),
            "location" to PermissionSet(
                required = listOf(FOREGROUND_SERVICE_LOCATION),
                oneOf = listOf(listOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION))
            ),
            "mediaPlayback" to PermissionSet(
                required = listOf(FOREGROUND_SERVICE_MEDIA_PLAYBACK)
            ),
            "mediaProjection" to PermissionSet(
                required = listOf(FOREGROUND_SERVICE_MEDIA_PROJECTION)
            ),
            "microphone" to PermissionSet(
                required = listOf(FOREGROUND_SERVICE_MICROPHONE, RECORD_AUDIO)
            ),
            "phoneCall" to PermissionSet(
                required = listOf(FOREGROUND_SERVICE_PHONE_CALL, MANAGE_OWN_CALLS)
            ),
            "remoteMessaging" to PermissionSet(
                required = listOf(FOREGROUND_SERVICE_REMOTE_MESSAGING)
            ),
            "shortService" to PermissionSet(
                required = listOf(FOREGROUND_SERVICE_SHORT_SERVICE)
            ),
            "specialUse" to PermissionSet(
                required = listOf(FOREGROUND_SERVICE_SPECIAL_USE)
            ),
            "systemExempted" to PermissionSet(
                required = listOf(FOREGROUND_SERVICE_SYSTEM_EXEMPTED)
            )
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                Starting with Android 14 (API 34), each foregroundServiceType declared on a \
                <service> requires specific permissions to be declared in the manifest. If any \
                required permission is missing, a SecurityException will be thrown when the \
                foreground service is started.
            """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}