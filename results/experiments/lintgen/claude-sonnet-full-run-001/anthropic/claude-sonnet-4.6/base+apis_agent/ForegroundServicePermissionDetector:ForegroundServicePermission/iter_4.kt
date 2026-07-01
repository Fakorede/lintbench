package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

/**
 * Detector that checks for missing permissions required by foregroundServiceType
 * when targetSdkVersion >= 34.
 */
class ForegroundServicePermissionDetector : Detector(), XmlScanner {

    companion object {
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
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )

        private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"
        private const val ATTR_TARGET_SDK_VERSION = "targetSdkVersion"
        private const val TAG_USES_SDK = "uses-sdk"

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
        private const val TYPE_FILE_MANAGEMENT = "fileManagement"

        // Permission constants
        private const val PERMISSION_CAMERA = "android.permission.CAMERA"
        private const val PERMISSION_BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
        private const val PERMISSION_BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
        private const val PERMISSION_CHANGE_NETWORK_STATE = "android.permission.CHANGE_NETWORK_STATE"
        private const val PERMISSION_CHANGE_WIFI_STATE = "android.permission.CHANGE_WIFI_STATE"
        private const val PERMISSION_CHANGE_WIFI_MULTICAST_STATE = "android.permission.CHANGE_WIFI_MULTICAST_STATE"
        private const val PERMISSION_NFC = "android.permission.NFC"
        private const val PERMISSION_TRANSMIT_IR = "android.permission.TRANSMIT_IR"
        private const val PERMISSION_UWB_RANGING = "android.permission.UWB_RANGING"
        private const val PERMISSION_BODY_SENSORS = "android.permission.BODY_SENSORS"
        private const val PERMISSION_ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
        private const val PERMISSION_ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
        private const val PERMISSION_RECORD_AUDIO = "android.permission.RECORD_AUDIO"
        private const val PERMISSION_MANAGE_OWN_CALLS = "android.permission.MANAGE_OWN_CALLS"
        private const val PERMISSION_READ_PHONE_NUMBERS = "android.permission.READ_PHONE_NUMBERS"
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
        private const val PERMISSION_FOREGROUND_SERVICE_FILE_MANAGEMENT = "android.permission.FOREGROUND_SERVICE_FILE_MANAGEMENT"
        private const val PERMISSION_MANAGE_MEDIA = "android.permission.MANAGE_MEDIA"
        private const val PERMISSION_READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val PERMISSION_READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
        private const val PERMISSION_READ_MEDIA_AUDIO = "android.permission.READ_MEDIA_AUDIO"
        private const val PERMISSION_MANAGE_DOCUMENTS = "android.permission.MANAGE_DOCUMENTS"

        /**
         * For each foreground service type, the required permissions.
         * Each inner list is a set of alternatives (any one of them satisfies the requirement).
         * The outer list contains groups where ALL groups must be satisfied.
         *
         * Structure: Map<serviceType, List<Set<permission>>> where each Set is a group of
         * alternatives (at least one must be present), and all groups must be satisfied.
         */
        val REQUIRED_PERMISSIONS: Map<String, List<Set<String>>> = mapOf(
            TYPE_CAMERA to listOf(
                setOf(PERMISSION_FOREGROUND_SERVICE_CAMERA),
                setOf(PERMISSION_CAMERA)
            ),
            TYPE_CONNECTED_DEVICE to listOf(
                setOf(PERMISSION_FOREGROUND_SERVICE_CONNECTED_DEVICE),
                setOf(
                    PERMISSION_BLUETOOTH_CONNECT,
                    PERMISSION_BLUETOOTH_SCAN,
                    PERMISSION_CHANGE_NETWORK_STATE,
                    PERMISSION_CHANGE_WIFI_STATE,
                    PERMISSION_CHANGE_WIFI_MULTICAST_STATE,
                    PERMISSION_NFC,
                    PERMISSION_TRANSMIT_IR,
                    PERMISSION_UWB_RANGING
                )
            ),
            TYPE_DATA_SYNC to listOf(
                setOf(PERMISSION_FOREGROUND_SERVICE_DATA_SYNC)
            ),
            TYPE_FILE_MANAGEMENT to listOf(
                setOf(PERMISSION_FOREGROUND_SERVICE_FILE_MANAGEMENT),
                setOf(
                    PERMISSION_MANAGE_MEDIA,
                    PERMISSION_READ_MEDIA_IMAGES,
                    PERMISSION_READ_MEDIA_VIDEO,
                    PERMISSION_READ_MEDIA_AUDIO,
                    PERMISSION_MANAGE_DOCUMENTS
                )
            ),
            TYPE_HEALTH to listOf(
                setOf(PERMISSION_FOREGROUND_SERVICE_HEALTH),
                setOf(
                    PERMISSION_BODY_SENSORS,
                    PERMISSION_ACCESS_FINE_LOCATION,
                    PERMISSION_ACCESS_COARSE_LOCATION,
                    PERMISSION_RECORD_AUDIO,
                    PERMISSION_CAMERA
                )
            ),
            TYPE_LOCATION to listOf(
                setOf(PERMISSION_FOREGROUND_SERVICE_LOCATION),
                setOf(PERMISSION_ACCESS_FINE_LOCATION, PERMISSION_ACCESS_COARSE_LOCATION)
            ),
            TYPE_MEDIA_PLAYBACK to listOf(
                setOf(PERMISSION_FOREGROUND_SERVICE_MEDIA_PLAYBACK)
            ),
            TYPE_MEDIA_PROJECTION to listOf(
                setOf(PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION)
            ),
            TYPE_MICROPHONE to listOf(
                setOf(PERMISSION_FOREGROUND_SERVICE_MICROPHONE),
                setOf(PERMISSION_RECORD_AUDIO)
            ),
            TYPE_PHONE_CALL to listOf(
                setOf(PERMISSION_FOREGROUND_SERVICE_PHONE_CALL),
                setOf(PERMISSION_MANAGE_OWN_CALLS, PERMISSION_READ_PHONE_NUMBERS)
            ),
            TYPE_REMOTE_MESSAGING to listOf(
                setOf(PERMISSION_FOREGROUND_SERVICE_REMOTE_MESSAGING)
            ),
            TYPE_SHORT_SERVICE to listOf(
                setOf(PERMISSION_FOREGROUND_SERVICE_SHORT_SERVICE)
            ),
            TYPE_SPECIAL_USE to listOf(
                setOf(PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE)
            ),
            TYPE_SYSTEM_EXEMPTED to listOf(
                setOf(PERMISSION_FOREGROUND_SERVICE_SYSTEM_EXEMPTED)
            )
        )

        // Numeric values for foreground service types (bitmask values from Android source)
        private val TYPE_NUMERIC_MAP: Map<Int, String> = mapOf(
            0x00000001 to TYPE_MEDIA_PLAYBACK,
            0x00000002 to TYPE_LOCATION,
            0x00000004 to TYPE_PHONE_CALL,
            0x00000008 to TYPE_CONNECTED_DEVICE,
            0x00000010 to TYPE_MEDIA_PROJECTION,
            0x00000020 to TYPE_CAMERA,
            0x00000040 to TYPE_MICROPHONE,
            0x00000080 to TYPE_HEALTH,
            0x00000100 to TYPE_REMOTE_MESSAGING,
            0x00000200 to TYPE_SYSTEM_EXEMPTED,
            0x00000400 to TYPE_SHORT_SERVICE,
            0x00000800 to TYPE_DATA_SYNC,
            0x00001000 to TYPE_FILE_MANAGEMENT,
            0x80000000.toInt() to TYPE_SPECIAL_USE
        )
    }

    // Collected declared permissions from the manifest
    private val declaredPermissions = mutableSetOf<String>()

    // Services with their foreground service types and the XML element for reporting
    private data class ServiceInfo(
        val element: Element,
        val serviceTypes: List<String>,
        val context: XmlContext
    )

    private val services = mutableListOf<ServiceInfo>()

    private var targetSdkVersion: Int = -1

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_SERVICE, TAG_USES_PERMISSION, TAG_USES_SDK)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_USES_SDK -> {
                val targetSdk = element.getAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION)
                if (targetSdk.isNotEmpty()) {
                    targetSdkVersion = targetSdk.toIntOrNull() ?: -1
                }
            }
            TAG_USES_PERMISSION -> {
                val permissionName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (permissionName.isNotEmpty()) {
                    declaredPermissions.add(permissionName)
                }
            }
            TAG_SERVICE -> {
                val foregroundServiceTypeAttr = element.getAttributeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
                if (foregroundServiceTypeAttr.isNotEmpty()) {
                    val serviceTypes = parseForegroundServiceTypes(foregroundServiceTypeAttr)
                    if (serviceTypes.isNotEmpty()) {
                        services.add(ServiceInfo(element, serviceTypes, context))
                    }
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        // Determine effective target SDK
        val effectiveTargetSdk = if (targetSdkVersion >= 0) {
            targetSdkVersion
        } else {
            context.project.targetSdk
        }

        // Only check if targetSdkVersion >= 34
        if (effectiveTargetSdk < 34) {
            reset()
            return
        }

        // Now check each service for missing permissions
        for (serviceInfo in services) {
            checkServicePermissions(serviceInfo)
        }

        reset()
    }

    private fun reset() {
        declaredPermissions.clear()
        services.clear()
        targetSdkVersion = -1
    }

    private fun checkServicePermissions(serviceInfo: ServiceInfo) {
        val context = serviceInfo.context
        val element = serviceInfo.element

        for (serviceType in serviceInfo.serviceTypes) {
            val requiredPermissionGroups = REQUIRED_PERMISSIONS[serviceType] ?: continue

            val missingGroups = mutableListOf<Set<String>>()

            for (permissionGroup in requiredPermissionGroups) {
                // Check if at least one permission from this group is declared
                val hasPermission = permissionGroup.any { it in declaredPermissions }
                if (!hasPermission) {
                    missingGroups.add(permissionGroup)
                }
            }

            if (missingGroups.isNotEmpty()) {
                val missingDescription = missingGroups.joinToString(" AND ") { group ->
                    if (group.size == 1) {
                        group.first()
                    } else {
                        "(one of: ${group.joinToString(", ")})"
                    }
                }

                val message = "Foreground service type `$serviceType` requires the following " +
                    "permission(s) to be declared in the manifest: $missingDescription. " +
                    "Starting a foreground service without these permissions on API 34+ will " +
                    "throw a `SecurityException`."

                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    message
                )
            }
        }
    }

    /**
     * Parse the foregroundServiceType attribute value into a list of type names.
     * The value can be a pipe-separated list of type names or a numeric bitmask.
     */
    private fun parseForegroundServiceTypes(value: String): List<String> {
        val trimmed = value.trim()

        // Try to parse as a numeric value (hex or decimal)
        val numericValue = when {
            trimmed.startsWith("0x") || trimmed.startsWith("0X") -> {
                trimmed.substring(2).toLongOrNull(16)?.toInt()
            }
            trimmed.all { it.isDigit() || it == '-' } -> {
                trimmed.toIntOrNull()
            }
            else -> null
        }

        if (numericValue != null) {
            return TYPE_NUMERIC_MAP.entries
                .filter { (mask, _) -> (numericValue and mask) != 0 }
                .map { it.value }
        }

        // Parse as pipe-separated type names
        return trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    }
}