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
        private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"
        private const val ATTR_TARGET_SDK_VERSION = "targetSdkVersion"
        private const val TAG_USES_SDK = "uses-sdk"

        // For connectedDevice type, at least one of these must be present
        private val CONNECTED_DEVICE_PERMISSIONS = setOf(
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.CHANGE_WIFI_MULTICAST_STATE",
            "android.permission.NFC",
            "android.permission.TRANSMIT_IR",
            "android.permission.UWB_RANGING"
        )

        // For location type, at least one of these must be present
        private val LOCATION_PERMISSIONS = setOf(
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_FINE_LOCATION"
        )

        // For health type, at least one of these must be present
        private val HEALTH_PERMISSIONS = setOf(
            "android.permission.ACTIVITY_RECOGNITION",
            "android.permission.BODY_SENSORS",
            "android.permission.HIGH_SAMPLING_RATE_SENSORS"
        )

        // For phoneCall type, at least one of these must be present
        private val PHONE_CALL_PERMISSIONS = setOf(
            "android.permission.MANAGE_OWN_CALLS",
            "android.permission.READ_PHONE_STATE"
        )

        // For remoteMessaging type, at least one of these must be present
        private val REMOTE_MESSAGING_PERMISSIONS = setOf(
            "android.permission.RECEIVE_SMS",
            "android.permission.READ_CONTACTS"
        )

        // For fileManagement type
        private val FILE_MANAGEMENT_PERMISSIONS = setOf(
            "android.permission.MANAGE_EXTERNAL_STORAGE"
        )

        // For systemExempted type, at least one of these must be present
        private val SYSTEM_EXEMPTED_PERMISSIONS = setOf(
            "android.permission.SCHEDULE_EXACT_ALARM",
            "android.permission.USE_EXACT_ALARM",
            "android.permission.DOWNLOAD_WITHOUT_NOTIFICATION",
            "android.permission.HIDE_OVERLAY_WINDOWS"
        )

        @JvmField
        val ISSUE_PERMISSION = Issue.create(
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
    }

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
                val permissionName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (permissionName.isNotEmpty()) {
                    declaredPermissions.add(permissionName)
                }
            }
            TAG_SERVICE -> {
                serviceElements.add(Pair(element, context))
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (targetSdkVersion < 34) {
            return
        }

        for ((serviceElement, xmlContext) in serviceElements) {
            checkServiceElement(serviceElement, xmlContext)
        }
    }

    private fun checkServiceElement(element: Element, context: XmlContext) {
        val foregroundServiceTypeAttr = element.getAttributeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
        if (foregroundServiceTypeAttr.isNullOrEmpty()) {
            return
        }

        val types = foregroundServiceTypeAttr.split("|").map { it.trim() }.filter { it.isNotEmpty() }

        for (type in types) {
            checkForegroundServiceType(type, element, context)
        }
    }

    private fun checkForegroundServiceType(type: String, element: Element, context: XmlContext) {
        when (type) {
            "camera" -> {
                if (!declaredPermissions.contains("android.permission.CAMERA")) {
                    reportMissingPermission(
                        context, element, type,
                        "android.permission.CAMERA"
                    )
                }
            }
            "connectedDevice" -> {
                val hasAnyPermission = CONNECTED_DEVICE_PERMISSIONS.any { declaredPermissions.contains(it) }
                if (!hasAnyPermission) {
                    reportMissingPermission(
                        context, element, type,
                        "at least one of: ${CONNECTED_DEVICE_PERMISSIONS.joinToString(", ")}"
                    )
                }
            }
            "dataSync" -> {
                if (!declaredPermissions.contains("android.permission.INTERNET")) {
                    reportMissingPermission(
                        context, element, type,
                        "android.permission.INTERNET"
                    )
                }
            }
            "fileManagement" -> {
                val hasAnyPermission = FILE_MANAGEMENT_PERMISSIONS.any { declaredPermissions.contains(it) }
                if (!hasAnyPermission) {
                    reportMissingPermission(
                        context, element, type,
                        "at least one of: ${FILE_MANAGEMENT_PERMISSIONS.joinToString(", ")}"
                    )
                }
            }
            "health" -> {
                val hasAnyPermission = HEALTH_PERMISSIONS.any { declaredPermissions.contains(it) }
                if (!hasAnyPermission) {
                    reportMissingPermission(
                        context, element, type,
                        "at least one of: ${HEALTH_PERMISSIONS.joinToString(", ")}"
                    )
                }
            }
            "location" -> {
                val hasAnyPermission = LOCATION_PERMISSIONS.any { declaredPermissions.contains(it) }
                if (!hasAnyPermission) {
                    reportMissingPermission(
                        context, element, type,
                        "at least one of: ${LOCATION_PERMISSIONS.joinToString(", ")}"
                    )
                }
            }
            "mediaProjection" -> {
                if (!declaredPermissions.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION")) {
                    reportMissingPermission(
                        context, element, type,
                        "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"
                    )
                }
            }
            "microphone" -> {
                if (!declaredPermissions.contains("android.permission.RECORD_AUDIO")) {
                    reportMissingPermission(
                        context, element, type,
                        "android.permission.RECORD_AUDIO"
                    )
                }
            }
            "phoneCall" -> {
                val hasAnyPermission = PHONE_CALL_PERMISSIONS.any { declaredPermissions.contains(it) }
                if (!hasAnyPermission) {
                    reportMissingPermission(
                        context, element, type,
                        "at least one of: ${PHONE_CALL_PERMISSIONS.joinToString(", ")}"
                    )
                }
            }
            "remoteMessaging" -> {
                val hasAnyPermission = REMOTE_MESSAGING_PERMISSIONS.any { declaredPermissions.contains(it) }
                if (!hasAnyPermission) {
                    reportMissingPermission(
                        context, element, type,
                        "at least one of: ${REMOTE_MESSAGING_PERMISSIONS.joinToString(", ")}"
                    )
                }
            }
            "specialUse" -> {
                if (!declaredPermissions.contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE")) {
                    reportMissingPermission(
                        context, element, type,
                        "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
                    )
                }
            }
            "systemExempted" -> {
                val hasAnyPermission = SYSTEM_EXEMPTED_PERMISSIONS.any { declaredPermissions.contains(it) }
                if (!hasAnyPermission) {
                    reportMissingPermission(
                        context, element, type,
                        "at least one of: ${SYSTEM_EXEMPTED_PERMISSIONS.joinToString(", ")}"
                    )
                }
            }
            // "mediaPlayback", "shortService" require no specific permissions
        }
    }

    private fun reportMissingPermission(
        context: XmlContext,
        element: Element,
        serviceType: String,
        requiredPermission: String
    ) {
        val serviceName = element.getAttributeNS(ANDROID_URI, ATTR_NAME).let {
            if (it.isNotEmpty()) " (${it})" else ""
        }
        val message = "Foreground service$serviceName with foregroundServiceType \"$serviceType\" " +
            "requires the permission(s): $requiredPermission. " +
            "Starting this foreground service without the required permissions will throw a " +
            "SecurityException on Android 14 (API 34) and higher."

        val locationNode = if (element.hasAttributeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)) {
            element.getAttributeNodeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
        } else {
            element
        }

        context.report(
            ISSUE_PERMISSION,
            element,
            context.getLocation(locationNode),
            message
        )
    }
}