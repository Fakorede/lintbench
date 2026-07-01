package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
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
                the manifest. If permissions are missing, then when the foreground service \
                is started with a `foregroundServiceType` that has missing permissions, a \
                `SecurityException` will be thrown.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        // Foreground service type constants
        private const val TYPE_CAMERA = "camera"
        private const val TYPE_CONNECTED_DEVICE = "connectedDevice"
        private const val TYPE_DATA_SYNC = "dataSync"
        private const val TYPE_HEALTH = "health"
        private const val TYPE_LOCATION = "location"
        private const val TYPE_MEDIA_PLAYBACK = "mediaPlayback"
        private const val TYPE_MEDIA_PROJECTION = "mediaProjection"
        private const val TYPE_MICROPHONE = "microphone"
        private const val TYPE_PHONE_CALL = "phoneCall"
        private const val TYPE_REMOTE_MESSAGING = "remoteMessaging"
        private const val TYPE_SHORT_SERVICE = "shortService"
        private const val TYPE_SPECIAL_USE = "specialUse"
        private const val TYPE_SYSTEM_EXEMPTED = "systemExempted"

        // Permission constants
        private const val PERM_CAMERA = "android.permission.CAMERA"
        private const val PERM_BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
        private const val PERM_BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
        private const val PERM_BLUETOOTH_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE"
        private const val PERM_CHANGE_NETWORK_STATE = "android.permission.CHANGE_NETWORK_STATE"
        private const val PERM_CHANGE_WIFI_STATE = "android.permission.CHANGE_WIFI_STATE"
        private const val PERM_CHANGE_WIFI_MULTICAST_STATE = "android.permission.CHANGE_WIFI_MULTICAST_STATE"
        private const val PERM_NFC = "android.permission.NFC"
        private const val PERM_TRANSMIT_IR = "android.permission.TRANSMIT_IR"
        private const val PERM_UWB_RANGING = "android.permission.UWB_RANGING"
        private const val PERM_BODY_SENSORS = "android.permission.BODY_SENSORS"
        private const val PERM_ACTIVITY_RECOGNITION = "android.permission.ACTIVITY_RECOGNITION"
        private const val PERM_ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
        private const val PERM_ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
        private const val PERM_RECORD_AUDIO = "android.permission.RECORD_AUDIO"
        private const val PERM_ACCEPT_HANDOVER = "android.permission.ACCEPT_HANDOVER"
        private const val PERM_MANAGE_OWN_CALLS = "android.permission.MANAGE_OWN_CALLS"
        private const val PERM_READ_PHONE_NUMBERS = "android.permission.READ_PHONE_NUMBERS"
        private const val PERM_READ_PHONE_STATE = "android.permission.READ_PHONE_STATE"
        private const val PERM_FOREGROUND_SERVICE_SPECIAL_USE =
            "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"

        // For each foreground service type, the required permission groups.
        // Each inner list is an "OR" group — at least one permission from the group must be present.
        // The outer list is "AND" — all groups must have at least one permission satisfied.
        private val TYPE_REQUIRED_PERMISSIONS: Map<String, List<List<String>>> = mapOf(
            TYPE_CAMERA to listOf(
                listOf(PERM_CAMERA)
            ),
            TYPE_CONNECTED_DEVICE to listOf(
                listOf(
                    PERM_BLUETOOTH_SCAN,
                    PERM_BLUETOOTH_CONNECT,
                    PERM_BLUETOOTH_ADVERTISE,
                    PERM_CHANGE_NETWORK_STATE,
                    PERM_CHANGE_WIFI_STATE,
                    PERM_CHANGE_WIFI_MULTICAST_STATE,
                    PERM_NFC,
                    PERM_TRANSMIT_IR,
                    PERM_UWB_RANGING,
                )
            ),
            TYPE_DATA_SYNC to listOf(
                // No specific permissions required beyond FOREGROUND_SERVICE
            ),
            TYPE_HEALTH to listOf(
                listOf(
                    PERM_BODY_SENSORS,
                    PERM_ACTIVITY_RECOGNITION,
                    PERM_ACCESS_FINE_LOCATION,
                    PERM_ACCESS_COARSE_LOCATION,
                )
            ),
            TYPE_LOCATION to listOf(
                listOf(
                    PERM_ACCESS_FINE_LOCATION,
                    PERM_ACCESS_COARSE_LOCATION,
                )
            ),
            TYPE_MEDIA_PLAYBACK to listOf(
                // No specific permissions required beyond FOREGROUND_SERVICE
            ),
            TYPE_MEDIA_PROJECTION to listOf(
                // Requires runtime permission obtained via MediaProjectionManager, not a manifest permission
            ),
            TYPE_MICROPHONE to listOf(
                listOf(PERM_RECORD_AUDIO)
            ),
            TYPE_PHONE_CALL to listOf(
                listOf(
                    PERM_ACCEPT_HANDOVER,
                    PERM_MANAGE_OWN_CALLS,
                    PERM_READ_PHONE_NUMBERS,
                    PERM_READ_PHONE_STATE,
                )
            ),
            TYPE_REMOTE_MESSAGING to listOf(
                // No specific permissions required beyond FOREGROUND_SERVICE
            ),
            TYPE_SHORT_SERVICE to listOf(
                // No specific permissions required beyond FOREGROUND_SERVICE
            ),
            TYPE_SPECIAL_USE to listOf(
                listOf(PERM_FOREGROUND_SERVICE_SPECIAL_USE)
            ),
            TYPE_SYSTEM_EXEMPTED to listOf(
                // System-only; not generally applicable
            ),
        )

        private const val ANDROID_MANIFEST_XML = "AndroidManifest.xml"
        private const val TAG_SERVICE = "service"
        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"
        private const val ATTR_NAME = "name"
        private const val ATTR_TARGET_SDK_VERSION = "targetSdkVersion"
        private const val TAG_USES_SDK = "uses-sdk"

        private const val MIN_TARGET_SDK = 34
    }

    override fun getApplicableElements(): Collection<String> = listOf(TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        // Only applies to AndroidManifest.xml
        if (!context.file.name.equals(ANDROID_MANIFEST_XML, ignoreCase = true)) {
            return
        }

        // Check targetSdkVersion >= 34
        val targetSdk = getTargetSdkVersion(context, element)
        if (targetSdk < MIN_TARGET_SDK) {
            return
        }

        // Get the foregroundServiceType attribute
        val foregroundServiceTypeAttr = element.getAttributeNodeNS(
            SdkConstants.ANDROID_URI,
            ATTR_FOREGROUND_SERVICE_TYPE
        ) ?: return

        val foregroundServiceTypeValue = foregroundServiceTypeAttr.value.trim()
        if (foregroundServiceTypeValue.isEmpty()) {
            return
        }

        // Collect all declared permissions in the manifest
        val declaredPermissions = getDeclaredPermissions(element)

        // Parse the foregroundServiceType value — it can be a '|'-separated list of types
        val serviceTypes = foregroundServiceTypeValue.split("|").map { it.trim() }.filter { it.isNotEmpty() }

        for (serviceType in serviceTypes) {
            val requiredPermissionGroups = TYPE_REQUIRED_PERMISSIONS[serviceType] ?: continue

            val missingGroups = mutableListOf<List<String>>()
            for (permissionGroup in requiredPermissionGroups) {
                val hasAtLeastOne = permissionGroup.any { perm -> declaredPermissions.contains(perm) }
                if (!hasAtLeastOne) {
                    missingGroups.add(permissionGroup)
                }
            }

            if (missingGroups.isNotEmpty()) {
                val missingDesc = missingGroups.joinToString(separator = " and ") { group ->
                    if (group.size == 1) {
                        "`${group[0]}`"
                    } else {
                        "one of [${group.joinToString(", ") { "`$it`" }}]"
                    }
                }
                val message = "Foreground service type `$serviceType` requires the permission(s): " +
                    "$missingDesc to be declared in the manifest"

                context.report(
                    issue = ISSUE,
                    element = element,
                    location = context.getLocation(foregroundServiceTypeAttr),
                    message = message,
                )
            }
        }
    }

    /**
     * Collect all permissions declared in the manifest via <uses-permission> elements.
     */
    private fun getDeclaredPermissions(serviceElement: Element): Set<String> {
        val permissions = mutableSetOf<String>()

        // Walk up to the manifest root
        var node: org.w3c.dom.Node? = serviceElement.ownerDocument?.documentElement
        if (node == null) {
            node = serviceElement
            while (node?.parentNode != null) {
                node = node.parentNode
            }
        }

        val root = node as? Element ?: return permissions
        collectPermissions(root, permissions)
        return permissions
    }

    private fun collectPermissions(element: Element, permissions: MutableSet<String>) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName == TAG_USES_PERMISSION || child.localName == TAG_USES_PERMISSION) {
                val permName = child.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_NAME)
                    .takeIf { it.isNotEmpty() }
                    ?: child.getAttribute("android:name")
                if (permName.isNotEmpty()) {
                    permissions.add(permName)
                }
            }
            collectPermissions(child, permissions)
        }
    }

    /**
     * Attempt to read targetSdkVersion from the manifest's <uses-sdk> element.
     * Falls back to the context's project target SDK if available.
     */
    private fun getTargetSdkVersion(context: XmlContext, serviceElement: Element): Int {
        // First try from context project
        val project = context.project
        val targetSdk = project.targetSdk
        if (targetSdk > 0) {
            return targetSdk
        }

        // Fall back: parse <uses-sdk> from the document
        val document = serviceElement.ownerDocument ?: return 0
        val root = document.documentElement ?: return 0
        val children = root.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName == TAG_USES_SDK || child.localName == TAG_USES_SDK) {
                val targetSdkStr = child.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_TARGET_SDK_VERSION)
                    .takeIf { it.isNotEmpty() }
                    ?: child.getAttribute("android:$ATTR_TARGET_SDK_VERSION")
                return targetSdkStr.toIntOrNull() ?: 0
            }
        }
        return 0
    }
}