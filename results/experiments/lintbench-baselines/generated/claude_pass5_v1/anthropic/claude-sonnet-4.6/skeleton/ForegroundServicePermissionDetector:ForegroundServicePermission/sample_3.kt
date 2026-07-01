package com.android.tools.lint.checks

import com.android.SdkConstants
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

        // Tag and attribute constants
        private const val TAG_SERVICE = "service"
        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val TAG_USES_PERMISSION_SDK_23 = "uses-permission-sdk-23"
        private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"
        private const val ATTR_NAME = "name"

        // Foreground service type constants (values from android:foregroundServiceType attribute)
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
        private const val PERMISSION_FOREGROUND_SERVICE = "android.permission.FOREGROUND_SERVICE"
        private const val PERMISSION_FOREGROUND_SERVICE_CAMERA = "android.permission.FOREGROUND_SERVICE_CAMERA"
        private const val PERMISSION_FOREGROUND_SERVICE_CONNECTED_DEVICE = "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"
        private const val PERMISSION_FOREGROUND_SERVICE_DATA_SYNC = "android.permission.FOREGROUND_SERVICE_DATA_SYNC"
        private const val PERMISSION_FOREGROUND_SERVICE_HEALTH = "android.permission.FOREGROUND_SERVICE_HEALTH"
        private const val PERMISSION_FOREGROUND_SERVICE_LOCATION = "android.permission.FOREGROUND_SERVICE_LOCATION"
        private const val PERMISSION_FOREGROUND_SERVICE_MEDIA_PLAYBACK = "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"
        private const val PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION = "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"
        private const val PERMISSION_FOREGROUND_SERVICE_MICROPHONE = "android.permission.FOREGROUND_SERVICE_MICROPHONE"
        private const val PERMISSION_FOREGROUND_SERVICE_PHONE_CALL = "android.permission.FOREGROUND_SERVICE_PHONE_CALL"
        private const val PERMISSION_FOREGROUND_SERVICE_REMOTE_MESSAGING = "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING"
        private const val PERMISSION_FOREGROUND_SERVICE_SHORT_SERVICE = "android.permission.FOREGROUND_SERVICE_SHORT_SERVICE"
        private const val PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE = "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
        private const val PERMISSION_FOREGROUND_SERVICE_SYSTEM_EXEMPTED = "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"

        private const val PERMISSION_CAMERA = "android.permission.CAMERA"
        private const val PERMISSION_ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
        private const val PERMISSION_ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
        private const val PERMISSION_RECORD_AUDIO = "android.permission.RECORD_AUDIO"
        private const val PERMISSION_CALL_PHONE = "android.permission.CALL_PHONE"
        private const val PERMISSION_ANSWER_PHONE_CALLS = "android.permission.ANSWER_PHONE_CALLS"
        private const val PERMISSION_MANAGE_OWN_CALLS = "android.permission.MANAGE_OWN_CALLS"

        private const val PERMISSION_BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
        private const val PERMISSION_BLUETOOTH_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE"
        private const val PERMISSION_BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
        private const val PERMISSION_CHANGE_NETWORK_STATE = "android.permission.CHANGE_NETWORK_STATE"
        private const val PERMISSION_CHANGE_WIFI_STATE = "android.permission.CHANGE_WIFI_STATE"
        private const val PERMISSION_CHANGE_WIFI_MULTICAST_STATE = "android.permission.CHANGE_WIFI_MULTICAST_STATE"
        private const val PERMISSION_NFC = "android.permission.NFC"
        private const val PERMISSION_TRANSMIT_IR = "android.permission.TRANSMIT_IR"
        private const val PERMISSION_UWB_RANGING = "android.permission.UWB_RANGING"

        private const val PERMISSION_ACTIVITY_RECOGNITION = "android.permission.ACTIVITY_RECOGNITION"
        private const val PERMISSION_BODY_SENSORS = "android.permission.BODY_SENSORS"
        private const val PERMISSION_HIGH_SAMPLING_RATE_SENSORS = "android.permission.HIGH_SAMPLING_RATE_SENSORS"

        // The minimum target SDK version for which these checks apply
        private const val MIN_TARGET_SDK = 34

        /**
         * Returns the sets of required permissions for a given foreground service type.
         * Each inner list represents an OR group (any one of the permissions in the group suffices).
         * All groups (outer list) must be satisfied.
         *
         * The base FOREGROUND_SERVICE permission is always required but is handled separately.
         */
        private fun getRequiredPermissionGroups(serviceType: String): List<List<String>> {
            return when (serviceType) {
                TYPE_CAMERA -> listOf(
                    listOf(PERMISSION_FOREGROUND_SERVICE_CAMERA),
                    listOf(PERMISSION_CAMERA),
                )
                TYPE_CONNECTED_DEVICE -> listOf(
                    listOf(PERMISSION_FOREGROUND_SERVICE_CONNECTED_DEVICE),
                    listOf(
                        PERMISSION_BLUETOOTH_CONNECT,
                        PERMISSION_BLUETOOTH_ADVERTISE,
                        PERMISSION_BLUETOOTH_SCAN,
                        PERMISSION_CHANGE_NETWORK_STATE,
                        PERMISSION_CHANGE_WIFI_STATE,
                        PERMISSION_CHANGE_WIFI_MULTICAST_STATE,
                        PERMISSION_NFC,
                        PERMISSION_TRANSMIT_IR,
                        PERMISSION_UWB_RANGING,
                    ),
                )
                TYPE_DATA_SYNC -> listOf(
                    listOf(PERMISSION_FOREGROUND_SERVICE_DATA_SYNC),
                )
                TYPE_HEALTH -> listOf(
                    listOf(PERMISSION_FOREGROUND_SERVICE_HEALTH),
                    listOf(
                        PERMISSION_ACTIVITY_RECOGNITION,
                        PERMISSION_BODY_SENSORS,
                        PERMISSION_HIGH_SAMPLING_RATE_SENSORS,
                    ),
                )
                TYPE_LOCATION -> listOf(
                    listOf(PERMISSION_FOREGROUND_SERVICE_LOCATION),
                    listOf(
                        PERMISSION_ACCESS_FINE_LOCATION,
                        PERMISSION_ACCESS_COARSE_LOCATION,
                    ),
                )
                TYPE_MEDIA_PLAYBACK -> listOf(
                    listOf(PERMISSION_FOREGROUND_SERVICE_MEDIA_PLAYBACK),
                )
                TYPE_MEDIA_PROJECTION -> listOf(
                    listOf(PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION),
                )
                TYPE_MICROPHONE -> listOf(
                    listOf(PERMISSION_FOREGROUND_SERVICE_MICROPHONE),
                    listOf(PERMISSION_RECORD_AUDIO),
                )
                TYPE_PHONE_CALL -> listOf(
                    listOf(PERMISSION_FOREGROUND_SERVICE_PHONE_CALL),
                    listOf(
                        PERMISSION_CALL_PHONE,
                        PERMISSION_ANSWER_PHONE_CALLS,
                        PERMISSION_MANAGE_OWN_CALLS,
                    ),
                )
                TYPE_REMOTE_MESSAGING -> listOf(
                    listOf(PERMISSION_FOREGROUND_SERVICE_REMOTE_MESSAGING),
                )
                TYPE_SHORT_SERVICE -> listOf(
                    listOf(PERMISSION_FOREGROUND_SERVICE_SHORT_SERVICE),
                )
                TYPE_SPECIAL_USE -> listOf(
                    listOf(PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE),
                )
                TYPE_SYSTEM_EXEMPTED -> listOf(
                    listOf(PERMISSION_FOREGROUND_SERVICE_SYSTEM_EXEMPTED),
                )
                else -> emptyList()
            }
        }
    }

    override fun getApplicableElements(): Collection<String> = listOf(TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        // Only apply this check for targetSdkVersion >= 34
        val project = context.project
        val targetSdk = project.targetSdk
        if (targetSdk < MIN_TARGET_SDK) {
            return
        }

        // Get the foregroundServiceType attribute value
        val foregroundServiceTypeValue = element.getAttributeNS(
            SdkConstants.ANDROID_URI,
            ATTR_FOREGROUND_SERVICE_TYPE
        ).takeIf { it.isNotEmpty() } ?: return

        // Collect all declared permissions in the manifest
        val declaredPermissions = collectDeclaredPermissions(context)

        // Parse the foreground service types (pipe-separated)
        val serviceTypes = foregroundServiceTypeValue.split("|").map { it.trim() }.filter { it.isNotEmpty() }

        for (serviceType in serviceTypes) {
            val requiredGroups = getRequiredPermissionGroups(serviceType)
            val missingGroups = mutableListOf<List<String>>()

            for (group in requiredGroups) {
                // Check if at least one permission from this group is declared
                val satisfied = group.any { permission -> declaredPermissions.contains(permission) }
                if (!satisfied) {
                    missingGroups.add(group)
                }
            }

            if (missingGroups.isNotEmpty()) {
                val missingDescription = missingGroups.joinToString(separator = " and ") { group ->
                    if (group.size == 1) {
                        group[0]
                    } else {
                        "one of [${group.joinToString(", ")}]"
                    }
                }

                val typeAttrNode = element.getAttributeNodeNS(
                    SdkConstants.ANDROID_URI,
                    ATTR_FOREGROUND_SERVICE_TYPE
                )

                val location = if (typeAttrNode != null) {
                    context.getLocation(typeAttrNode)
                } else {
                    context.getLocation(element)
                }

                context.report(
                    issue = ISSUE,
                    scope = element,
                    location = location,
                    message = "Foreground service type `$serviceType` requires the following " +
                        "permission(s) to be declared: $missingDescription"
                )
            }
        }
    }

    /**
     * Collects all permissions declared in the manifest via <uses-permission> elements.
     */
    private fun collectDeclaredPermissions(context: XmlContext): Set<String> {
        val permissions = mutableSetOf<String>()
        val document = context.document
        val manifestElement = document.documentElement ?: return permissions

        val children = manifestElement.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element) {
                val tagName = node.localName ?: node.nodeName
                if (tagName == TAG_USES_PERMISSION || tagName == TAG_USES_PERMISSION_SDK_23) {
                    val permName = node.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_NAME)
                    if (permName.isNotEmpty()) {
                        permissions.add(permName)
                    }
                }
            }
        }
        return permissions
    }
}