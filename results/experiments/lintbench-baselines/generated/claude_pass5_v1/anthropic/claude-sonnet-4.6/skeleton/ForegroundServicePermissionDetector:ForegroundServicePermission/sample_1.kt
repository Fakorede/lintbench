package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import java.util.EnumSet
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ForegroundServicePermissionDetector::class.java,
            EnumSet.of(Scope.MANIFEST),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For `targetSdkVersion` >= 34, each `foregroundServiceType` listed in the \
                `<service>` element requires specific sets of permissions to be declared in \
                the manifest. If permissions are missing, then when the foreground service is \
                started with a `foregroundServiceType` that has missing permissions, a \
                `SecurityException` will be thrown.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        // Mapping from foregroundServiceType to required permission sets.
        // Each inner list represents an OR group (any one permission from the group satisfies the requirement).
        // The outer list represents AND groups (all groups must be satisfied).
        // Format: Map<serviceType, List<Set<permission>>> where each Set is an OR group.
        private val FOREGROUND_SERVICE_TYPE_PERMISSIONS: Map<String, List<Set<String>>> = mapOf(
            "camera" to listOf(
                setOf("android.permission.CAMERA")
            ),
            "connectedDevice" to listOf(
                setOf(
                    "android.permission.BLUETOOTH_ADVERTISE",
                    "android.permission.BLUETOOTH_CONNECT",
                    "android.permission.BLUETOOTH_SCAN",
                    "android.permission.CHANGE_NETWORK_STATE",
                    "android.permission.CHANGE_WIFI_STATE",
                    "android.permission.CHANGE_WIFI_MULTICAST_STATE",
                    "android.permission.NFC",
                    "android.permission.TRANSMIT_IR",
                    "android.permission.UWB_RANGING",
                )
            ),
            "dataSync" to listOf(
                // No specific permissions required beyond FOREGROUND_SERVICE_DATA_SYNC
                // but the type itself requires the permission
            ),
            "health" to listOf(
                setOf(
                    "android.permission.ACTIVITY_RECOGNITION",
                    "android.permission.BODY_SENSORS",
                    "android.permission.HIGH_SAMPLING_RATE_SENSORS",
                )
            ),
            "location" to listOf(
                setOf(
                    "android.permission.ACCESS_COARSE_LOCATION",
                    "android.permission.ACCESS_FINE_LOCATION",
                )
            ),
            "mediaPlayback" to listOf(
                // No extra permissions beyond the foreground service type permission
            ),
            "mediaProjection" to listOf(
                // Requires runtime permission granted via MediaProjectionManager
            ),
            "microphone" to listOf(
                setOf("android.permission.RECORD_AUDIO")
            ),
            "phoneCall" to listOf(
                setOf(
                    "android.permission.MANAGE_OWN_CALLS",
                    "android.permission.READ_PHONE_STATE",
                )
            ),
            "remoteMessaging" to listOf(
                // No specific permissions required
            ),
            "shortService" to listOf(
                // No specific permissions required
            ),
            "specialUse" to listOf(
                // Requires android.permission.FOREGROUND_SERVICE_SPECIAL_USE
            ),
            "systemExempted" to listOf(
                // System apps only
            ),
        )

        // Map from foregroundServiceType to the FOREGROUND_SERVICE_* permission required
        private val FOREGROUND_SERVICE_TYPE_SELF_PERMISSIONS: Map<String, String> = mapOf(
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
            "systemExempted" to "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED",
        )

        private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"
        private const val ATTR_NAME = "name"
        private const val TAG_USES_SDK = "uses-sdk"
        private const val ATTR_TARGET_SDK_VERSION = "targetSdkVersion"
        private const val MIN_TARGET_SDK = 34
    }

    override fun getApplicableElements(): Collection<String> = listOf(TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        // Only check if targetSdkVersion >= 34
        val targetSdk = getTargetSdkVersion(context, element)
        if (targetSdk < MIN_TARGET_SDK) return

        // Get the foregroundServiceType attribute
        val foregroundServiceTypeAttr = element.getAttributeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
        if (foregroundServiceTypeAttr.isNullOrEmpty()) return

        // Parse the foregroundServiceType (can be multiple types separated by |)
        val serviceTypes = foregroundServiceTypeAttr.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        if (serviceTypes.isEmpty()) return

        // Collect all declared permissions in the manifest
        val declaredPermissions = getDeclaredPermissions(context, element)

        // Check each service type
        for (serviceType in serviceTypes) {
            // Check the FOREGROUND_SERVICE_* permission for this type
            val selfPermission = FOREGROUND_SERVICE_TYPE_SELF_PERMISSIONS[serviceType]
            if (selfPermission != null && selfPermission !in declaredPermissions) {
                val attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
                val location = if (attr != null) context.getLocation(attr) else context.getLocation(element)
                context.report(
                    ISSUE,
                    element,
                    location,
                    "Foreground service type `$serviceType` requires permission `$selfPermission` to be declared in the manifest",
                )
            }

            // Check the additional permissions required by this service type
            val requiredPermissionGroups = FOREGROUND_SERVICE_TYPE_PERMISSIONS[serviceType] ?: continue
            for (permissionGroup in requiredPermissionGroups) {
                if (permissionGroup.isEmpty()) continue
                // Check if at least one permission from the group is declared
                val hasPermission = permissionGroup.any { it in declaredPermissions }
                if (!hasPermission) {
                    val attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
                    val location = if (attr != null) context.getLocation(attr) else context.getLocation(element)
                    val permissionsStr = permissionGroup.sorted().joinToString(" or ") { "`$it`" }
                    context.report(
                        ISSUE,
                        element,
                        location,
                        "Foreground service type `$serviceType` requires at least one of the following permissions to be declared in the manifest: $permissionsStr",
                    )
                }
            }
        }
    }

    private fun getTargetSdkVersion(context: XmlContext, element: Element): Int {
        // Walk up to find the manifest document and look for uses-sdk
        val document = element.ownerDocument ?: return 0
        val manifestElement = document.documentElement ?: return 0

        val usesSdkElements = manifestElement.getElementsByTagName(TAG_USES_SDK)
        for (i in 0 until usesSdkElements.length) {
            val usesSdk = usesSdkElements.item(i) as? Element ?: continue
            val targetSdkStr = usesSdk.getAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION)
            if (targetSdkStr.isNotEmpty()) {
                return targetSdkStr.toIntOrNull() ?: 0
            }
        }

        // Fall back to project's target SDK
        val project = context.project
        return project.targetSdk
    }

    private fun getDeclaredPermissions(context: XmlContext, element: Element): Set<String> {
        val permissions = mutableSetOf<String>()
        val document = element.ownerDocument ?: return permissions
        val manifestElement = document.documentElement ?: return permissions

        val permissionElements = manifestElement.getElementsByTagName(TAG_USES_PERMISSION)
        for (i in 0 until permissionElements.length) {
            val permissionElement = permissionElements.item(i) as? Element ?: continue
            val name = permissionElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name.isNotEmpty()) {
                permissions.add(name)
            }
        }

        return permissions
    }
}