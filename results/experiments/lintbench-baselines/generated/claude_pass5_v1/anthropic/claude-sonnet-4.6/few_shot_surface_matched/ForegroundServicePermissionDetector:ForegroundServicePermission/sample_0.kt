package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
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

    companion object {
        private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"
        private const val MIN_TARGET_SDK = 34

        // Mapping from foregroundServiceType value to the sets of permissions required.
        // Each inner list is a set of alternative permissions (ANY one suffices for that group).
        // All groups must be satisfied.
        private val TYPE_REQUIRED_PERMISSIONS: Map<String, List<List<String>>> = mapOf(
            "camera" to listOf(
                listOf("android.permission.CAMERA")
            ),
            "connectedDevice" to listOf(
                listOf(
                    "android.permission.BLUETOOTH_CONNECT",
                    "android.permission.BLUETOOTH_ADVERTISE",
                    "android.permission.BLUETOOTH_SCAN",
                    "android.permission.UWB_RANGING",
                    "android.permission.CHANGE_NETWORK_STATE",
                    "android.permission.CHANGE_WIFI_STATE",
                    "android.permission.CHANGE_WIFI_MULTICAST_STATE",
                    "android.permission.NFC",
                    "android.permission.TRANSMIT_IR"
                )
            ),
            "dataSync" to listOf(
                // No specific permission required beyond FOREGROUND_SERVICE
            ),
            "health" to listOf(
                listOf(
                    "android.permission.ACTIVITY_RECOGNITION",
                    "android.permission.BODY_SENSORS",
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.ACCESS_COARSE_LOCATION"
                )
            ),
            "location" to listOf(
                listOf(
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.ACCESS_COARSE_LOCATION"
                )
            ),
            "mediaPlayback" to listOf(
                // No specific permission required beyond FOREGROUND_SERVICE
            ),
            "mediaProjection" to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION")
            ),
            "microphone" to listOf(
                listOf("android.permission.RECORD_AUDIO")
            ),
            "phoneCall" to listOf(
                listOf(
                    "android.permission.MANAGE_OWN_CALLS",
                    "android.permission.READ_PHONE_STATE"
                )
            ),
            "remoteMessaging" to listOf(
                // No specific permission required beyond FOREGROUND_SERVICE
            ),
            "shortService" to listOf(
                // No specific permission required beyond FOREGROUND_SERVICE
            ),
            "specialUse" to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_SPECIAL_USE")
            ),
            "systemExempted" to listOf(
                // System-exempted; no specific user permission required
            )
        )

        // The foreground service type-specific permissions introduced in API 34
        private val FOREGROUND_SERVICE_TYPE_PERMISSIONS: Map<String, String> = mapOf(
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
            "shortService" to "android.permission.FOREGROUND_SERVICE_SHORT_SERVICE",
            "specialUse" to "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            "systemExempted" to "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For `targetSdkVersion` >= 34, each `foregroundServiceType` listed in the \
                `<service>` element requires specific sets of permissions to be declared in \
                the manifest. If permissions are missing, then when the foreground service \
                is started with a `foregroundServiceType` that has missing permissions, a \
                `SecurityException` will be thrown.
            """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            ),
            androidSpecific = true
        )
    }

    override fun getApplicableElements(): Collection<String> = listOf(TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        // Only check for targetSdkVersion >= 34
        val project = context.project
        val targetSdk = project.targetSdk
        if (targetSdk < MIN_TARGET_SDK) return

        // Get the foregroundServiceType attribute
        val fgServiceTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
            ?: return
        val fgServiceTypeValue = fgServiceTypeAttr.value.trim()
        if (fgServiceTypeValue.isEmpty()) return

        // Parse the pipe-separated list of types
        val types = fgServiceTypeValue.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        if (types.isEmpty()) return

        // Collect all declared permissions from the manifest
        val declaredPermissions = collectDeclaredPermissions(context)

        // Check each type
        for (type in types) {
            // Check for the API-34 foreground service type permission
            val fgTypePermission = FOREGROUND_SERVICE_TYPE_PERMISSIONS[type]
            if (fgTypePermission != null && !declaredPermissions.contains(fgTypePermission)) {
                val location = context.getValueLocation(fgServiceTypeAttr)
                context.report(
                    ISSUE,
                    element,
                    location,
                    "Foreground service type `$type` requires the permission " +
                        "`$fgTypePermission` to be declared in the manifest"
                )
            }

            // Check for the data/capability permissions required by this type
            val requiredPermissionGroups = TYPE_REQUIRED_PERMISSIONS[type] ?: continue
            for (permissionGroup in requiredPermissionGroups) {
                if (permissionGroup.isEmpty()) continue
                // At least one permission from the group must be declared
                val hasAtLeastOne = permissionGroup.any { declaredPermissions.contains(it) }
                if (!hasAtLeastOne) {
                    val location = context.getValueLocation(fgServiceTypeAttr)
                    val permList = permissionGroup.joinToString(" or ") { "`$it`" }
                    context.report(
                        ISSUE,
                        element,
                        location,
                        "Foreground service type `$type` requires at least one of the " +
                            "following permissions to be declared in the manifest: $permList"
                    )
                }
            }
        }
    }

    private fun collectDeclaredPermissions(context: XmlContext): Set<String> {
        val document = context.document
        val root = document.documentElement ?: return emptySet()
        val permissions = mutableSetOf<String>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_USES_PERMISSION) {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name.isNotEmpty()) {
                    permissions.add(name)
                }
            }
        }
        return permissions
    }
}