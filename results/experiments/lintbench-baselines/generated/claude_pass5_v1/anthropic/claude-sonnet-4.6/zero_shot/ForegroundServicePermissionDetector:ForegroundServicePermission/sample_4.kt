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

        // Foreground service type values
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

        // Required permissions per foreground service type (at least one from each group must be present)
        // Structure: Map<serviceType, List<PermissionGroup>> where PermissionGroup is a set of alternatives
        private val REQUIRED_PERMISSIONS: Map<String, List<Set<String>>> = mapOf(
            TYPE_CAMERA to listOf(
                setOf("android.permission.CAMERA")
            ),
            TYPE_CONNECTED_DEVICE to listOf(
                setOf(
                    "android.permission.BLUETOOTH_ADVERTISE",
                    "android.permission.BLUETOOTH_CONNECT",
                    "android.permission.BLUETOOTH_SCAN",
                    "android.permission.CHANGE_NETWORK_STATE",
                    "android.permission.CHANGE_WIFI_MULTICAST_STATE",
                    "android.permission.CHANGE_WIFI_STATE",
                    "android.permission.NFC",
                    "android.permission.TRANSMIT_IR",
                    "android.permission.UWB_RANGING"
                )
            ),
            TYPE_DATA_SYNC to listOf(
                // dataSync requires FOREGROUND_SERVICE_DATA_SYNC in API 34+
                setOf("android.permission.FOREGROUND_SERVICE_DATA_SYNC")
            ),
            TYPE_HEALTH to listOf(
                setOf(
                    "android.permission.ACTIVITY_RECOGNITION",
                    "android.permission.BODY_SENSORS",
                    "android.permission.HIGH_SAMPLING_RATE_SENSORS"
                )
            ),
            TYPE_LOCATION to listOf(
                setOf(
                    "android.permission.ACCESS_COARSE_LOCATION",
                    "android.permission.ACCESS_FINE_LOCATION"
                )
            ),
            TYPE_MEDIA_PLAYBACK to listOf(
                // mediaPlayback requires FOREGROUND_SERVICE_MEDIA_PLAYBACK in API 34+
                setOf("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK")
            ),
            TYPE_MEDIA_PROJECTION to listOf(
                setOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION")
            ),
            TYPE_MICROPHONE to listOf(
                setOf("android.permission.RECORD_AUDIO")
            ),
            TYPE_PHONE_CALL to listOf(
                setOf(
                    "android.permission.MANAGE_OWN_CALLS",
                    "android.permission.READ_PHONE_STATE"
                )
            ),
            TYPE_REMOTE_MESSAGING to listOf(
                setOf("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING")
            ),
            TYPE_SHORT_SERVICE to emptyList(), // No additional permissions needed
            TYPE_SPECIAL_USE to listOf(
                setOf("android.permission.FOREGROUND_SERVICE_SPECIAL_USE")
            ),
            TYPE_SYSTEM_EXEMPTED to listOf(
                setOf("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED")
            )
        )

        // Foreground service type permission declarations needed (API 34+)
        private val FOREGROUND_SERVICE_TYPE_PERMISSIONS: Map<String, String> = mapOf(
            TYPE_CAMERA to "android.permission.FOREGROUND_SERVICE_CAMERA",
            TYPE_CONNECTED_DEVICE to "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
            TYPE_DATA_SYNC to "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            TYPE_HEALTH to "android.permission.FOREGROUND_SERVICE_HEALTH",
            TYPE_LOCATION to "android.permission.FOREGROUND_SERVICE_LOCATION",
            TYPE_MEDIA_PLAYBACK to "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            TYPE_MEDIA_PROJECTION to "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
            TYPE_MICROPHONE to "android.permission.FOREGROUND_SERVICE_MICROPHONE",
            TYPE_PHONE_CALL to "android.permission.FOREGROUND_SERVICE_PHONE_CALL",
            TYPE_REMOTE_MESSAGING to "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING",
            TYPE_SHORT_SERVICE to "android.permission.FOREGROUND_SERVICE_SHORT_SERVICE",
            TYPE_SPECIAL_USE to "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            TYPE_SYSTEM_EXEMPTED to "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"
        )

        // The base FOREGROUND_SERVICE permission required for all foreground services
        private const val FOREGROUND_SERVICE_PERMISSION = "android.permission.FOREGROUND_SERVICE"

        // Mapping from numeric foreground service type values to string names
        private val NUMERIC_TYPE_MAP: Map<Int, String> = mapOf(
            0x00000001 to TYPE_DATA_SYNC,
            0x00000002 to TYPE_MEDIA_PLAYBACK,
            0x00000004 to TYPE_PHONE_CALL,
            0x00000008 to TYPE_LOCATION,
            0x00000010 to TYPE_CONNECTED_DEVICE,
            0x00000020 to TYPE_MEDIA_PROJECTION,
            0x00000040 to TYPE_CAMERA,
            0x00000080 to TYPE_MICROPHONE,
            0x00000100 to TYPE_HEALTH,
            0x00000200 to TYPE_REMOTE_MESSAGING,
            0x00000400 to TYPE_SYSTEM_EXEMPTED,
            0x00000800 to TYPE_SHORT_SERVICE,
            0x80000000.toInt() to TYPE_SPECIAL_USE
        )
    }

    private var targetSdkVersion: Int = -1
    private val declaredPermissions = mutableSetOf<String>()

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_SERVICE, TAG_USES_PERMISSION, TAG_USES_SDK)
    }

    override fun beforeCheckFile(context: Context) {
        targetSdkVersion = -1
        declaredPermissions.clear()
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
                // We'll handle service elements in afterCheckFile after we've
                // collected all permissions. But we need to process in document order.
                // Since XML is parsed top to bottom, uses-sdk and uses-permission
                // should come before service elements. However, to be safe, we
                // collect services and check them after.
                checkServiceElement(context, element)
            }
        }
    }

    private fun checkServiceElement(context: XmlContext, element: Element) {
        // Only check if targetSdkVersion >= 34
        // If targetSdkVersion is not set, we check the manifest's compileSdkVersion as fallback
        val effectiveTargetSdk = if (targetSdkVersion >= 0) targetSdkVersion else {
            // Try to get from context
            context.mainProject.targetSdk
        }

        if (effectiveTargetSdk < 34) {
            return
        }

        val foregroundServiceTypeAttr = element.getAttributeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
        if (foregroundServiceTypeAttr.isNullOrEmpty()) {
            return
        }

        // Parse the foreground service types (can be pipe-separated or a numeric value)
        val serviceTypes = parseForegroundServiceTypes(foregroundServiceTypeAttr)
        if (serviceTypes.isEmpty()) {
            return
        }

        // Check FOREGROUND_SERVICE permission for all foreground services
        if (!declaredPermissions.contains(FOREGROUND_SERVICE_PERMISSION)) {
            // This is a separate issue (PermissionImpliesUnsupportedChromeOsHardware handles this)
            // but we can still mention it
        }

        for (serviceType in serviceTypes) {
            checkServiceType(context, element, serviceType)
        }
    }

    private fun parseForegroundServiceTypes(typeAttr: String): List<String> {
        val result = mutableListOf<String>()

        // Try parsing as numeric (hex or decimal)
        val numericValue = parseNumericValue(typeAttr)
        if (numericValue != null) {
            // Parse bitmask
            for ((bit, typeName) in NUMERIC_TYPE_MAP) {
                if (numericValue and bit != 0) {
                    result.add(typeName)
                }
            }
            return result
        }

        // Parse as pipe-separated string values
        val parts = typeAttr.split("|").map { it.trim() }
        for (part in parts) {
            if (part.isNotEmpty()) {
                // Handle fully qualified names like "android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA"
                val simpleName = extractSimpleTypeName(part)
                if (simpleName != null) {
                    result.add(simpleName)
                } else {
                    result.add(part)
                }
            }
        }
        return result
    }

    private fun parseNumericValue(value: String): Int? {
        return try {
            when {
                value.startsWith("0x") || value.startsWith("0X") ->
                    value.substring(2).toLong(16).toInt()
                value.all { it.isDigit() || it == '-' } ->
                    value.toInt()
                else -> null
            }
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun extractSimpleTypeName(typeName: String): String? {
        // Map from constant names to simple type names
        val constantToType = mapOf(
            "FOREGROUND_SERVICE_TYPE_CAMERA" to TYPE_CAMERA,
            "FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE" to TYPE_CONNECTED_DEVICE,
            "FOREGROUND_SERVICE_TYPE_DATA_SYNC" to TYPE_DATA_SYNC,
            "FOREGROUND_SERVICE_TYPE_HEALTH" to TYPE_HEALTH,
            "FOREGROUND_SERVICE_TYPE_LOCATION" to TYPE_LOCATION,
            "FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK" to TYPE_MEDIA_PLAYBACK,
            "FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION" to TYPE_MEDIA_PROJECTION,
            "FOREGROUND_SERVICE_TYPE_MICROPHONE" to TYPE_MICROPHONE,
            "FOREGROUND_SERVICE_TYPE_PHONE_CALL" to TYPE_PHONE_CALL,
            "FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING" to TYPE_REMOTE_MESSAGING,
            "FOREGROUND_SERVICE_TYPE_SHORT_SERVICE" to TYPE_SHORT_SERVICE,
            "FOREGROUND_SERVICE_TYPE_SPECIAL_USE" to TYPE_SPECIAL_USE,
            "FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED" to TYPE_SYSTEM_EXEMPTED
        )

        val simpleName = typeName.substringAfterLast(".")
        return constantToType[simpleName]
    }

    private fun checkServiceType(context: XmlContext, element: Element, serviceType: String) {
        val missingPermissions = mutableListOf<String>()

        // Check if the foreground service type permission is declared (API 34+)
        val fgsTypePermission = FOREGROUND_SERVICE_TYPE_PERMISSIONS[serviceType]
        if (fgsTypePermission != null && !declaredPermissions.contains(fgsTypePermission)) {
            // For some types, the type permission IS the required permission
            when (serviceType) {
                TYPE_DATA_SYNC, TYPE_MEDIA_PLAYBACK, TYPE_MEDIA_PROJECTION,
                TYPE_REMOTE_MESSAGING, TYPE_SHORT_SERVICE, TYPE_SPECIAL_USE,
                TYPE_SYSTEM_EXEMPTED -> {
                    missingPermissions.add(fgsTypePermission)
                }
                else -> {
                    // For other types, check the required permissions groups
                    checkRequiredPermissionGroups(serviceType, missingPermissions)
                    // Also check the type-specific permission
                    if (!declaredPermissions.contains(fgsTypePermission)) {
                        missingPermissions.add(fgsTypePermission)
                    }
                }
            }
        } else {
            // Type permission is present or not required; check other required permissions
            when (serviceType) {
                TYPE_SHORT_SERVICE -> {
                    // No additional permissions needed
                }
                else -> {
                    checkRequiredPermissionGroups(serviceType, missingPermissions)
                }
            }
        }

        if (missingPermissions.isNotEmpty()) {
            val serviceName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
            val serviceDisplayName = if (serviceName.isNotEmpty()) " ($serviceName)" else ""

            val message = buildString {
                append("Foreground service type `$serviceType`$serviceDisplayName requires ")
                if (missingPermissions.size == 1) {
                    append("permission `${missingPermissions[0]}`")
                } else {
                    append("permissions: ")
                    append(missingPermissions.joinToString(", ") { "`$it`" })
                }
                append(" to be declared in the manifest")
            }

            context.report(
                issue = ISSUE,
                element = element,
                location = context.getLocation(element),
                message = message
            )
        }
    }

    private fun checkRequiredPermissionGroups(serviceType: String, missingPermissions: MutableList<String>) {
        val requiredGroups = REQUIRED_PERMISSIONS[serviceType] ?: return

        for (permissionGroup in requiredGroups) {
            // At least one permission from the group must be declared
            val hasAny = permissionGroup.any { declaredPermissions.contains(it) }
            if (!hasAny) {
                if (permissionGroup.size == 1) {
                    missingPermissions.add(permissionGroup.first())
                } else {
                    // Report that at least one of these permissions is needed
                    missingPermissions.add("one of: ${permissionGroup.sorted().joinToString(", ")}")
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        // Nothing needed here since we check during visitElement
    }
}