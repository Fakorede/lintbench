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
import java.util.EnumSet

class ForegroundServicePermissionDetector : Detector(), XmlScanner {

    companion object {
        private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"
        private const val ATTR_TARGET_SDK_VERSION = "targetSdkVersion"
        private const val ATTR_MIN_SDK_VERSION = "minSdkVersion"
        private const val TAG_USES_SDK = "uses-sdk"
        private const val TAG_MANIFEST = "manifest"

        // Foreground service type constants (as used in the manifest)
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
        private const val PERMISSION_MANAGE_OWN_CALLS = "android.permission.MANAGE_OWN_CALLS"
        private const val PERMISSION_READ_PHONE_NUMBERS = "android.permission.READ_PHONE_NUMBERS"
        private const val PERMISSION_READ_PHONE_STATE = "android.permission.READ_PHONE_STATE"
        private const val PERMISSION_BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
        private const val PERMISSION_BLUETOOTH_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE"
        private const val PERMISSION_BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
        private const val PERMISSION_UWB_RANGING = "android.permission.UWB_RANGING"
        private const val PERMISSION_CHANGE_NETWORK_STATE = "android.permission.CHANGE_NETWORK_STATE"
        private const val PERMISSION_CHANGE_WIFI_STATE = "android.permission.CHANGE_WIFI_STATE"
        private const val PERMISSION_CHANGE_WIFI_MULTICAST_STATE = "android.permission.CHANGE_WIFI_MULTICAST_STATE"
        private const val PERMISSION_NFC = "android.permission.NFC"
        private const val PERMISSION_TRANSMIT_IR = "android.permission.TRANSMIT_IR"
        private const val PERMISSION_BODY_SENSORS = "android.permission.BODY_SENSORS"
        private const val PERMISSION_ACTIVITY_RECOGNITION = "android.permission.ACTIVITY_RECOGNITION"
        private const val PERMISSION_HIGH_SAMPLING_RATE_SENSORS = "android.permission.HIGH_SAMPLING_RATE_SENSORS"
        private const val PERMISSION_USE_SIP = "android.permission.USE_SIP"
        private const val PERMISSION_BIND_TELECOM_CONNECTION_SERVICE = "android.permission.BIND_TELECOM_CONNECTION_SERVICE"

        // Required type-specific permission per foreground service type (Android 14+)
        private val TYPE_REQUIRED_PERMISSION = mapOf(
            TYPE_CAMERA to PERMISSION_FOREGROUND_SERVICE_CAMERA,
            TYPE_CONNECTED_DEVICE to PERMISSION_FOREGROUND_SERVICE_CONNECTED_DEVICE,
            TYPE_DATA_SYNC to PERMISSION_FOREGROUND_SERVICE_DATA_SYNC,
            TYPE_HEALTH to PERMISSION_FOREGROUND_SERVICE_HEALTH,
            TYPE_LOCATION to PERMISSION_FOREGROUND_SERVICE_LOCATION,
            TYPE_MEDIA_PLAYBACK to PERMISSION_FOREGROUND_SERVICE_MEDIA_PLAYBACK,
            TYPE_MEDIA_PROJECTION to PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION,
            TYPE_MICROPHONE to PERMISSION_FOREGROUND_SERVICE_MICROPHONE,
            TYPE_PHONE_CALL to PERMISSION_FOREGROUND_SERVICE_PHONE_CALL,
            TYPE_REMOTE_MESSAGING to PERMISSION_FOREGROUND_SERVICE_REMOTE_MESSAGING,
            TYPE_SHORT_SERVICE to PERMISSION_FOREGROUND_SERVICE_SHORT_SERVICE,
            TYPE_SPECIAL_USE to PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE,
            TYPE_SYSTEM_EXEMPTED to PERMISSION_FOREGROUND_SERVICE_SYSTEM_EXEMPTED
        )

        // Additional permissions required per type (at least one from each set must be present)
        // Each entry is a list of permission sets; at least one permission from each set is needed.
        private val TYPE_ADDITIONAL_PERMISSIONS: Map<String, List<Set<String>>> = mapOf(
            TYPE_CAMERA to listOf(
                setOf(PERMISSION_CAMERA)
            ),
            TYPE_LOCATION to listOf(
                setOf(PERMISSION_ACCESS_FINE_LOCATION, PERMISSION_ACCESS_COARSE_LOCATION)
            ),
            TYPE_MICROPHONE to listOf(
                setOf(PERMISSION_RECORD_AUDIO)
            ),
            TYPE_PHONE_CALL to listOf(
                setOf(
                    PERMISSION_MANAGE_OWN_CALLS,
                    PERMISSION_READ_PHONE_NUMBERS,
                    PERMISSION_READ_PHONE_STATE,
                    PERMISSION_USE_SIP,
                    PERMISSION_BIND_TELECOM_CONNECTION_SERVICE
                )
            ),
            TYPE_CONNECTED_DEVICE to listOf(
                setOf(
                    PERMISSION_BLUETOOTH_CONNECT,
                    PERMISSION_BLUETOOTH_ADVERTISE,
                    PERMISSION_BLUETOOTH_SCAN,
                    PERMISSION_UWB_RANGING,
                    PERMISSION_CHANGE_NETWORK_STATE,
                    PERMISSION_CHANGE_WIFI_STATE,
                    PERMISSION_CHANGE_WIFI_MULTICAST_STATE,
                    PERMISSION_NFC,
                    PERMISSION_TRANSMIT_IR
                )
            ),
            TYPE_HEALTH to listOf(
                setOf(
                    PERMISSION_BODY_SENSORS,
                    PERMISSION_ACTIVITY_RECOGNITION,
                    PERMISSION_HIGH_SAMPLING_RATE_SENSORS,
                    PERMISSION_ACCESS_FINE_LOCATION,
                    PERMISSION_ACCESS_COARSE_LOCATION
                )
            )
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
                EnumSet.of(Scope.MANIFEST)
            )
        )
    }

    /** Set of permissions declared in the manifest */
    private val declaredPermissions = mutableSetOf<String>()

    /** List of service elements with foregroundServiceType to check after full manifest parse */
    private val serviceElements = mutableListOf<Pair<Element, XmlContext>>()

    /** Target SDK version from the manifest */
    private var targetSdkVersion = -1

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
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name.isNotEmpty()) {
                    declaredPermissions.add(name)
                }
            }
            TAG_SERVICE -> {
                val foregroundServiceType = element.getAttributeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
                if (foregroundServiceType.isNotEmpty()) {
                    serviceElements.add(Pair(element, context))
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (targetSdkVersion < 34) {
            // Only applicable for targetSdkVersion >= 34
            serviceElements.clear()
            declaredPermissions.clear()
            return
        }

        for ((element, xmlContext) in serviceElements) {
            checkServiceElement(element, xmlContext)
        }

        serviceElements.clear()
        declaredPermissions.clear()
        targetSdkVersion = -1
    }

    private fun checkServiceElement(element: Element, context: XmlContext) {
        val foregroundServiceTypeAttr = element.getAttributeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
        if (foregroundServiceTypeAttr.isEmpty()) return

        // Parse the foregroundServiceType value - it can be a pipe-separated list of types
        // or a numeric value (flags). We handle string types here.
        val types = parseTypes(foregroundServiceTypeAttr)

        // Check FOREGROUND_SERVICE permission is declared (required for all foreground services)
        if (!declaredPermissions.contains(PERMISSION_FOREGROUND_SERVICE)) {
            val location = context.getNameLocation(element)
            context.report(
                ISSUE,
                element,
                location,
                "Missing required permission `android.permission.FOREGROUND_SERVICE` for foreground service"
            )
        }

        for (type in types) {
            checkType(element, context, type)
        }
    }

    private fun parseTypes(foregroundServiceType: String): List<String> {
        // The value can be pipe-separated: "camera|microphone" or a single type
        // It could also be a hex/integer flag value - we only handle string type names
        return foregroundServiceType.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun checkType(element: Element, context: XmlContext, type: String) {
        // Check the type-specific foreground service permission
        val requiredTypePermission = TYPE_REQUIRED_PERMISSION[type]
        if (requiredTypePermission != null) {
            if (!declaredPermissions.contains(requiredTypePermission)) {
                val location = context.getNameLocation(element)
                context.report(
                    ISSUE,
                    element,
                    location,
                    "Foreground service type `$type` requires permission `$requiredTypePermission`"
                )
            }
        }

        // Check additional permissions required for this type
        val additionalPermissionSets = TYPE_ADDITIONAL_PERMISSIONS[type]
        if (additionalPermissionSets != null) {
            for (permissionSet in additionalPermissionSets) {
                val hasAtLeastOne = permissionSet.any { declaredPermissions.contains(it) }
                if (!hasAtLeastOne) {
                    val location = context.getNameLocation(element)
                    val permList = permissionSet.joinToString(", ") { "`$it`" }
                    context.report(
                        ISSUE,
                        element,
                        location,
                        "Foreground service type `$type` requires at least one of the following permissions: $permList"
                    )
                }
            }
        }
    }
}