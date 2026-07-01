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
        private const val PERMISSION_CAMERA = "android.permission.CAMERA"
        private const val PERMISSION_BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
        private const val PERMISSION_BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
        private const val PERMISSION_BLUETOOTH_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE"
        private const val PERMISSION_CHANGE_NETWORK_STATE = "android.permission.CHANGE_NETWORK_STATE"
        private const val PERMISSION_CHANGE_WIFI_STATE = "android.permission.CHANGE_WIFI_STATE"
        private const val PERMISSION_CHANGE_WIFI_MULTICAST_STATE = "android.permission.CHANGE_WIFI_MULTICAST_STATE"
        private const val PERMISSION_NFC = "android.permission.NFC"
        private const val PERMISSION_TRANSMIT_IR = "android.permission.TRANSMIT_IR"
        private const val PERMISSION_UWB_RANGING = "android.permission.UWB_RANGING"
        private const val PERMISSION_BODY_SENSORS = "android.permission.BODY_SENSORS"
        private const val PERMISSION_ACTIVITY_RECOGNITION = "android.permission.ACTIVITY_RECOGNITION"
        private const val PERMISSION_ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
        private const val PERMISSION_ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
        private const val PERMISSION_ACCESS_BACKGROUND_LOCATION = "android.permission.ACCESS_BACKGROUND_LOCATION"
        private const val PERMISSION_RECORD_AUDIO = "android.permission.RECORD_AUDIO"
        private const val PERMISSION_ACCEPT_HANDOVER = "android.permission.ACCEPT_HANDOVER"
        private const val PERMISSION_MANAGE_OWN_CALLS = "android.permission.MANAGE_OWN_CALLS"
        private const val PERMISSION_READ_PHONE_NUMBERS = "android.permission.READ_PHONE_NUMBERS"
        private const val PERMISSION_READ_PHONE_STATE = "android.permission.READ_PHONE_STATE"
        private const val PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE = "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"

        // Foreground service type specific permissions
        // Each entry: type -> list of permission groups where at least one from each group must be declared
        // Using List<List<String>> where inner list means "at least one of these"
        private val TYPE_REQUIRED_PERMISSIONS: Map<String, List<List<String>>> = mapOf(
            TYPE_CAMERA to listOf(
                listOf(PERMISSION_CAMERA)
            ),
            TYPE_CONNECTED_DEVICE to listOf(
                listOf(
                    PERMISSION_BLUETOOTH_SCAN,
                    PERMISSION_BLUETOOTH_CONNECT,
                    PERMISSION_BLUETOOTH_ADVERTISE,
                    PERMISSION_CHANGE_NETWORK_STATE,
                    PERMISSION_CHANGE_WIFI_STATE,
                    PERMISSION_CHANGE_WIFI_MULTICAST_STATE,
                    PERMISSION_NFC,
                    PERMISSION_TRANSMIT_IR,
                    PERMISSION_UWB_RANGING,
                )
            ),
            TYPE_HEALTH to listOf(
                listOf(
                    PERMISSION_BODY_SENSORS,
                    PERMISSION_ACTIVITY_RECOGNITION,
                    PERMISSION_ACCESS_FINE_LOCATION,
                    PERMISSION_ACCESS_COARSE_LOCATION,
                    PERMISSION_ACCESS_BACKGROUND_LOCATION,
                )
            ),
            TYPE_LOCATION to listOf(
                listOf(
                    PERMISSION_ACCESS_FINE_LOCATION,
                    PERMISSION_ACCESS_COARSE_LOCATION,
                )
            ),
            TYPE_MICROPHONE to listOf(
                listOf(PERMISSION_RECORD_AUDIO)
            ),
            TYPE_PHONE_CALL to listOf(
                listOf(
                    PERMISSION_MANAGE_OWN_CALLS,
                    PERMISSION_READ_PHONE_NUMBERS,
                )
            ),
            TYPE_SPECIAL_USE to listOf(
                listOf(PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE)
            ),
        )

        // Types that do not require any special permissions
        private val TYPES_WITHOUT_PERMISSION_REQUIREMENTS = setOf(
            TYPE_DATA_SYNC,
            TYPE_MEDIA_PLAYBACK,
            TYPE_MEDIA_PROJECTION,
            TYPE_REMOTE_MESSAGING,
            TYPE_SHORT_SERVICE,
            TYPE_SYSTEM_EXEMPTED,
        )

        private const val TAG_SERVICE = "service"
        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val TAG_USES_PERMISSION_SDK_23 = "uses-permission-sdk-23"
        private const val TAG_USES_PERMISSION_SDK_M = "uses-permission-sdk-m"
        private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"
        private const val ATTR_NAME = "name"
        private const val ANDROID_MANIFEST_XML = "AndroidManifest.xml"

        private const val MIN_TARGET_SDK_VERSION = 34
    }

    override fun getApplicableElements(): Collection<String> = listOf(TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        // Only check for targetSdkVersion >= 34
        val project = context.project
        val targetSdk = project.targetSdk
        if (targetSdk < MIN_TARGET_SDK_VERSION) {
            return
        }

        // Get the foregroundServiceType attribute
        val foregroundServiceTypeAttr = element.getAttributeNodeNS(
            SdkConstants.ANDROID_URI,
            ATTR_FOREGROUND_SERVICE_TYPE
        ) ?: return

        val foregroundServiceTypeValue = foregroundServiceTypeAttr.value
        if (foregroundServiceTypeValue.isBlank()) {
            return
        }

        // Parse the foreground service types (pipe-separated)
        val serviceTypes = foregroundServiceTypeValue.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        if (serviceTypes.isEmpty()) {
            return
        }

        // Collect all declared permissions in the manifest
        val declaredPermissions = collectDeclaredPermissions(context)

        // Check each service type
        for (serviceType in serviceTypes) {
            if (serviceType in TYPES_WITHOUT_PERMISSION_REQUIREMENTS) {
                continue
            }

            val requiredPermissionGroups = TYPE_REQUIRED_PERMISSIONS[serviceType] ?: continue

            // For each group, at least one permission must be declared
            val missingGroups = mutableListOf<List<String>>()
            for (permissionGroup in requiredPermissionGroups) {
                val hasAny = permissionGroup.any { it in declaredPermissions }
                if (!hasAny) {
                    missingGroups.add(permissionGroup)
                }
            }

            if (missingGroups.isNotEmpty()) {
                val missingDescription = missingGroups.joinToString(separator = " and ") { group ->
                    if (group.size == 1) {
                        "`${group[0]}`"
                    } else {
                        "one of [${group.joinToString(", ") { "`$it`" }}]"
                    }
                }
                val message = "Foreground service type `$serviceType` requires the permission " +
                    "$missingDescription to be declared in the manifest"
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(foregroundServiceTypeAttr),
                    message
                )
            }
        }
    }

    private fun collectDeclaredPermissions(context: XmlContext): Set<String> {
        val permissions = mutableSetOf<String>()
        val document = context.document
        val root = document.documentElement ?: return permissions

        val childNodes = root.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is Element) {
                val tagName = node.localName ?: node.tagName
                if (tagName == TAG_USES_PERMISSION ||
                    tagName == TAG_USES_PERMISSION_SDK_23 ||
                    tagName == TAG_USES_PERMISSION_SDK_M
                ) {
                    val permName = node.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_NAME)
                    if (permName.isNotBlank()) {
                        permissions.add(permName)
                    }
                }
            }
        }
        return permissions
    }
}