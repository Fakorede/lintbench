package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PERMISSION
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import java.util.EnumSet

class ForegroundServicePermissionDetector : Detector(), XmlScanner {

    companion object {
        private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"
        private const val MIN_TARGET_SDK = 34

        // Foreground service type constants (bit flags as used in Android)
        private const val TYPE_DATA_SYNC = "dataSync"
        private const val TYPE_MEDIA_PLAYBACK = "mediaPlayback"
        private const val TYPE_PHONE_CALL = "phoneCall"
        private const val TYPE_LOCATION = "location"
        private const val TYPE_CONNECTED_DEVICE = "connectedDevice"
        private const val TYPE_MEDIA_PROJECTION = "mediaProjection"
        private const val TYPE_CAMERA = "camera"
        private const val TYPE_MICROPHONE = "microphone"
        private const val TYPE_HEALTH = "health"
        private const val TYPE_REMOTE_MESSAGING = "remoteMessaging"
        private const val TYPE_SYSTEM_EXEMPTED = "systemExempted"
        private const val TYPE_SHORT_SERVICE = "shortService"
        private const val TYPE_FILE_MANAGEMENT = "fileManagement"
        private const val TYPE_SPECIAL_USE = "specialUse"

        // Permission groups required for each foreground service type
        // Each entry is: type -> list of permission groups (at least one permission from each group must be declared)
        private val REQUIRED_PERMISSIONS: Map<String, List<List<String>>> = mapOf(
            TYPE_DATA_SYNC to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_DATA_SYNC")
            ),
            TYPE_MEDIA_PLAYBACK to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK")
            ),
            TYPE_PHONE_CALL to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_PHONE_CALL"),
                listOf("android.permission.MANAGE_OWN_CALLS", "android.permission.READ_PHONE_STATE")
            ),
            TYPE_LOCATION to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_LOCATION"),
                listOf("android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION")
            ),
            TYPE_CONNECTED_DEVICE to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"),
                listOf(
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
            TYPE_MEDIA_PROJECTION to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION")
            ),
            TYPE_CAMERA to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_CAMERA"),
                listOf("android.permission.CAMERA")
            ),
            TYPE_MICROPHONE to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_MICROPHONE"),
                listOf("android.permission.RECORD_AUDIO", "android.permission.CAPTURE_AUDIO_OUTPUT")
            ),
            TYPE_HEALTH to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_HEALTH"),
                listOf(
                    "android.permission.ACTIVITY_RECOGNITION",
                    "android.permission.BODY_SENSORS",
                    "android.permission.HIGH_SAMPLING_RATE_SENSORS"
                )
            ),
            TYPE_REMOTE_MESSAGING to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING")
            ),
            TYPE_SYSTEM_EXEMPTED to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED")
            ),
            TYPE_SHORT_SERVICE to emptyList(), // No additional permissions required
            TYPE_FILE_MANAGEMENT to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_FILE_MANAGEMENT")
            ),
            TYPE_SPECIAL_USE to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_SPECIAL_USE")
            )
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For `targetSdkVersion` >= $MIN_TARGET_SDK, each `foregroundServiceType` listed \
                in the `<service>` element requires specific sets of permissions to be declared \
                in the manifest. If permissions are missing, then when the foreground service is \
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
    }

    /** Collected permissions declared in the manifest */
    private val declaredPermissions = mutableSetOf<String>()

    /** Service elements with their foreground service types and locations */
    private data class ServiceInfo(
        val element: Element,
        val location: Location,
        val types: List<String>
    )

    private val serviceInfoList = mutableListOf<ServiceInfo>()

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_PERMISSION, TAG_SERVICE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_USES_PERMISSION -> {
                val permName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (permName.isNotEmpty()) {
                    declaredPermissions.add(permName)
                }
            }
            TAG_SERVICE -> {
                val fgServiceType = element.getAttributeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
                if (fgServiceType.isNotEmpty()) {
                    val types = fgServiceType.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    if (types.isNotEmpty()) {
                        serviceInfoList.add(
                            ServiceInfo(
                                element = element,
                                location = context.getLocation(element),
                                types = types
                            )
                        )
                    }
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (context !is XmlContext) return

        val targetSdk = context.project.targetSdk
        if (targetSdk < MIN_TARGET_SDK) {
            serviceInfoList.clear()
            declaredPermissions.clear()
            return
        }

        for (serviceInfo in serviceInfoList) {
            for (type in serviceInfo.types) {
                val requiredPermissionGroups = REQUIRED_PERMISSIONS[type] ?: continue
                if (requiredPermissionGroups.isEmpty()) continue

                val missingGroups = mutableListOf<List<String>>()

                for (permissionGroup in requiredPermissionGroups) {
                    // Check if at least one permission from this group is declared
                    val hasPermission = permissionGroup.any { perm ->
                        declaredPermissions.contains(perm)
                    }
                    if (!hasPermission) {
                        missingGroups.add(permissionGroup)
                    }
                }

                if (missingGroups.isNotEmpty()) {
                    val serviceName = serviceInfo.element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                        .takeIf { it.isNotEmpty() } ?: "<unknown>"

                    val missingDesc = missingGroups.joinToString(", ") { group ->
                        if (group.size == 1) {
                            group[0]
                        } else {
                            "one of [${group.joinToString(", ")}]"
                        }
                    }

                    val message = "Foreground service ($serviceName) with foregroundServiceType " +
                        "\"$type\" requires the following permission(s) to be declared: $missingDesc"

                    context.report(
                        issue = ISSUE,
                        location = serviceInfo.location,
                        message = message
                    )
                }
            }
        }

        serviceInfoList.clear()
        declaredPermissions.clear()
    }
}