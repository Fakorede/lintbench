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

        // Permission constants
        private const val PERM_FOREGROUND_SERVICE = "android.permission.FOREGROUND_SERVICE"
        private const val PERM_FOREGROUND_SERVICE_CAMERA = "android.permission.FOREGROUND_SERVICE_CAMERA"
        private const val PERM_FOREGROUND_SERVICE_CONNECTED_DEVICE = "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"
        private const val PERM_FOREGROUND_SERVICE_DATA_SYNC = "android.permission.FOREGROUND_SERVICE_DATA_SYNC"
        private const val PERM_FOREGROUND_SERVICE_HEALTH = "android.permission.FOREGROUND_SERVICE_HEALTH"
        private const val PERM_FOREGROUND_SERVICE_LOCATION = "android.permission.FOREGROUND_SERVICE_LOCATION"
        private const val PERM_FOREGROUND_SERVICE_MEDIA_PLAYBACK = "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"
        private const val PERM_FOREGROUND_SERVICE_MEDIA_PROJECTION = "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"
        private const val PERM_FOREGROUND_SERVICE_MICROPHONE = "android.permission.FOREGROUND_SERVICE_MICROPHONE"
        private const val PERM_FOREGROUND_SERVICE_PHONE_CALL = "android.permission.FOREGROUND_SERVICE_PHONE_CALL"
        private const val PERM_FOREGROUND_SERVICE_REMOTE_MESSAGING = "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING"
        private const val PERM_FOREGROUND_SERVICE_SHORT_SERVICE = "android.permission.FOREGROUND_SERVICE_SHORT_SERVICE"
        private const val PERM_FOREGROUND_SERVICE_SPECIAL_USE = "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
        private const val PERM_FOREGROUND_SERVICE_SYSTEM_EXEMPTED = "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"

        private const val PERM_CAMERA = "android.permission.CAMERA"
        private const val PERM_LOCATION_FINE = "android.permission.ACCESS_FINE_LOCATION"
        private const val PERM_LOCATION_COARSE = "android.permission.ACCESS_COARSE_LOCATION"
        private const val PERM_RECORD_AUDIO = "android.permission.RECORD_AUDIO"
        private const val PERM_CALL_PHONE = "android.permission.CALL_PHONE"
        private const val PERM_ANSWER_PHONE_CALLS = "android.permission.ANSWER_PHONE_CALLS"
        private const val PERM_MANAGE_OWN_CALLS = "android.permission.MANAGE_OWN_CALLS"
        private const val PERM_BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
        private const val PERM_BLUETOOTH_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE"
        private const val PERM_BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
        private const val PERM_CHANGE_NETWORK_STATE = "android.permission.CHANGE_NETWORK_STATE"
        private const val PERM_CHANGE_WIFI_STATE = "android.permission.CHANGE_WIFI_STATE"
        private const val PERM_CHANGE_WIFI_MULTICAST_STATE = "android.permission.CHANGE_WIFI_MULTICAST_STATE"
        private const val PERM_NFC = "android.permission.NFC"
        private const val PERM_TRANSMIT_IR = "android.permission.TRANSMIT_IR"
        private const val PERM_UWB_RANGING = "android.permission.UWB_RANGING"
        private const val PERM_ACTIVITY_RECOGNITION = "android.permission.ACTIVITY_RECOGNITION"
        private const val PERM_BODY_SENSORS = "android.permission.BODY_SENSORS"
        private const val PERM_USE_SIP = "android.permission.USE_SIP"

        /**
         * Returns the required permission sets for a given foreground service type.
         * Each inner list represents an "OR" group - at least one permission from each
         * outer list group must be present.
         *
         * The structure is: List of requirement groups, where each group is satisfied
         * if at least one permission in the group is declared.
         */
        private fun getRequiredPermissionGroups(serviceType: String): List<RequirementGroup> {
            return when (serviceType) {
                TYPE_CAMERA -> listOf(
                    RequirementGroup(
                        description = "foreground service type permission",
                        permissions = setOf(PERM_FOREGROUND_SERVICE_CAMERA)
                    ),
                    RequirementGroup(
                        description = "camera permission",
                        permissions = setOf(PERM_CAMERA)
                    )
                )
                TYPE_CONNECTED_DEVICE -> listOf(
                    RequirementGroup(
                        description = "foreground service type permission",
                        permissions = setOf(PERM_FOREGROUND_SERVICE_CONNECTED_DEVICE)
                    ),
                    RequirementGroup(
                        description = "connected device permission",
                        permissions = setOf(
                            PERM_BLUETOOTH_ADVERTISE,
                            PERM_BLUETOOTH_CONNECT,
                            PERM_BLUETOOTH_SCAN,
                            PERM_CHANGE_NETWORK_STATE,
                            PERM_CHANGE_WIFI_STATE,
                            PERM_CHANGE_WIFI_MULTICAST_STATE,
                            PERM_NFC,
                            PERM_TRANSMIT_IR,
                            PERM_UWB_RANGING
                        )
                    )
                )
                TYPE_DATA_SYNC -> listOf(
                    RequirementGroup(
                        description = "foreground service type permission",
                        permissions = setOf(PERM_FOREGROUND_SERVICE_DATA_SYNC)
                    )
                )
                TYPE_HEALTH -> listOf(
                    RequirementGroup(
                        description = "foreground service type permission",
                        permissions = setOf(PERM_FOREGROUND_SERVICE_HEALTH)
                    ),
                    RequirementGroup(
                        description = "health permission",
                        permissions = setOf(
                            PERM_ACTIVITY_RECOGNITION,
                            PERM_BODY_SENSORS,
                            PERM_LOCATION_FINE,
                            PERM_LOCATION_COARSE
                        )
                    )
                )
                TYPE_LOCATION -> listOf(
                    RequirementGroup(
                        description = "foreground service type permission",
                        permissions = setOf(PERM_FOREGROUND_SERVICE_LOCATION)
                    ),
                    RequirementGroup(
                        description = "location permission",
                        permissions = setOf(PERM_LOCATION_FINE, PERM_LOCATION_COARSE)
                    )
                )
                TYPE_MEDIA_PLAYBACK -> listOf(
                    RequirementGroup(
                        description = "foreground service type permission",
                        permissions = setOf(PERM_FOREGROUND_SERVICE_MEDIA_PLAYBACK)
                    )
                )
                TYPE_MEDIA_PROJECTION -> listOf(
                    RequirementGroup(
                        description = "foreground service type permission",
                        permissions = setOf(PERM_FOREGROUND_SERVICE_MEDIA_PROJECTION)
                    )
                )
                TYPE_MICROPHONE -> listOf(
                    RequirementGroup(
                        description = "foreground service type permission",
                        permissions = setOf(PERM_FOREGROUND_SERVICE_MICROPHONE)
                    ),
                    RequirementGroup(
                        description = "microphone permission",
                        permissions = setOf(PERM_RECORD_AUDIO)
                    )
                )
                TYPE_PHONE_CALL -> listOf(
                    RequirementGroup(
                        description = "foreground service type permission",
                        permissions = setOf(PERM_FOREGROUND_SERVICE_PHONE_CALL)
                    ),
                    RequirementGroup(
                        description = "phone call permission",
                        permissions = setOf(
                            PERM_CALL_PHONE,
                            PERM_ANSWER_PHONE_CALLS,
                            PERM_MANAGE_OWN_CALLS,
                            PERM_USE_SIP
                        )
                    )
                )
                TYPE_REMOTE_MESSAGING -> listOf(
                    RequirementGroup(
                        description = "foreground service type permission",
                        permissions = setOf(PERM_FOREGROUND_SERVICE_REMOTE_MESSAGING)
                    )
                )
                TYPE_SHORT_SERVICE -> listOf(
                    RequirementGroup(
                        description = "foreground service type permission",
                        permissions = setOf(PERM_FOREGROUND_SERVICE_SHORT_SERVICE)
                    )
                )
                TYPE_SPECIAL_USE -> listOf(
                    RequirementGroup(
                        description = "foreground service type permission",
                        permissions = setOf(PERM_FOREGROUND_SERVICE_SPECIAL_USE)
                    )
                )
                TYPE_SYSTEM_EXEMPTED -> listOf(
                    RequirementGroup(
                        description = "foreground service type permission",
                        permissions = setOf(PERM_FOREGROUND_SERVICE_SYSTEM_EXEMPTED)
                    )
                )
                else -> emptyList()
            }
        }

        /**
         * All known foreground service types that require specific permissions in API 34+
         */
        private val KNOWN_FOREGROUND_SERVICE_TYPES = setOf(
            TYPE_CAMERA,
            TYPE_CONNECTED_DEVICE,
            TYPE_DATA_SYNC,
            TYPE_HEALTH,
            TYPE_LOCATION,
            TYPE_MEDIA_PLAYBACK,
            TYPE_MEDIA_PROJECTION,
            TYPE_MICROPHONE,
            TYPE_PHONE_CALL,
            TYPE_REMOTE_MESSAGING,
            TYPE_SHORT_SERVICE,
            TYPE_SPECIAL_USE,
            TYPE_SYSTEM_EXEMPTED
        )
    }

    /**
     * Represents a group of permissions where at least one must be present.
     */
    private data class RequirementGroup(
        val description: String,
        val permissions: Set<String>
    )

    private var targetSdkVersion: Int = -1
    private val declaredPermissions = mutableSetOf<String>()
    private val serviceElements = mutableListOf<Pair<Element, XmlContext>>()

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_SDK, TAG_USES_PERMISSION, TAG_SERVICE)
    }

    override fun beforeCheckFile(context: Context) {
        targetSdkVersion = -1
        declaredPermissions.clear()
        serviceElements.clear()
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
                val permName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (permName.isNotEmpty()) {
                    declaredPermissions.add(permName)
                }
            }
            TAG_SERVICE -> {
                serviceElements.add(Pair(element, context))
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        // Only check if targetSdkVersion >= 34
        if (targetSdkVersion < 34) {
            return
        }

        // Always include the base FOREGROUND_SERVICE permission
        // (it's implicitly required but we check the type-specific ones)

        for ((serviceElement, xmlContext) in serviceElements) {
            checkServiceElement(xmlContext, serviceElement)
        }
    }

    private fun checkServiceElement(context: XmlContext, element: Element) {
        val foregroundServiceTypeAttr = element.getAttributeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
        if (foregroundServiceTypeAttr.isNullOrEmpty()) {
            return
        }

        // The foregroundServiceType can be a combination of types using '|'
        val serviceTypes = foregroundServiceTypeAttr.split("|").map { it.trim() }.filter { it.isNotEmpty() }

        for (serviceType in serviceTypes) {
            // Strip any package prefix if present (e.g., android.foregroundServiceType.camera -> camera)
            val normalizedType = normalizeServiceType(serviceType)

            if (normalizedType !in KNOWN_FOREGROUND_SERVICE_TYPES) {
                continue
            }

            val requirementGroups = getRequiredPermissionGroups(normalizedType)

            for (group in requirementGroups) {
                val hasPermission = group.permissions.any { it in declaredPermissions }
                if (!hasPermission) {
                    val missingPermissions = group.permissions.sorted().joinToString(" or ") { "`$it`" }
                    val message = buildString {
                        append("Foreground service type `$normalizedType` requires ")
                        append(missingPermissions)
                        append(" to be declared in the manifest")
                        if (group.permissions.size > 1) {
                            append(" (at least one of these permissions is required)")
                        }
                    }

                    val location = context.getElementLocation(element)
                    context.report(
                        issue = ISSUE,
                        element = element,
                        location = location,
                        message = message
                    )
                    // Report once per service type per missing group
                    break
                }
            }
        }
    }

    private fun normalizeServiceType(serviceType: String): String {
        // Handle cases like "android.foregroundServiceType.camera" -> "camera"
        return when {
            serviceType.contains(".") -> serviceType.substringAfterLast(".")
            else -> serviceType
        }
    }
}