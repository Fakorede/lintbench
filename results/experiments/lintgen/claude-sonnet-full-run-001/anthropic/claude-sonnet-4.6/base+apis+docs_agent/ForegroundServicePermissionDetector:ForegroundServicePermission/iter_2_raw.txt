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

        // Types that require at least one permission from a group (OR logic within each inner list)
        private val TYPE_PERMISSION_GROUPS: Map<String, List<Pair<String, List<String>>>> = mapOf(
            "camera" to listOf(
                "camera access" to listOf("android.permission.CAMERA")
            ),
            "connectedDevice" to listOf(
                "connected device access" to listOf(
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
            ),
            "dataSync" to listOf(
                "network access" to listOf("android.permission.INTERNET")
            ),
            "fileManagement" to listOf(
                "file management access" to listOf(
                    "android.permission.MANAGE_EXTERNAL_STORAGE",
                    "android.permission.READ_MEDIA_IMAGES",
                    "android.permission.READ_MEDIA_VIDEO",
                    "android.permission.READ_MEDIA_AUDIO",
                    "android.permission.READ_EXTERNAL_STORAGE",
                    "android.permission.WRITE_EXTERNAL_STORAGE"
                )
            ),
            "health" to listOf(
                "health sensor access" to listOf(
                    "android.permission.ACTIVITY_RECOGNITION",
                    "android.permission.BODY_SENSORS",
                    "android.permission.HIGH_SAMPLING_RATE_SENSORS"
                )
            ),
            "location" to listOf(
                "location access" to listOf(
                    "android.permission.ACCESS_COARSE_LOCATION",
                    "android.permission.ACCESS_FINE_LOCATION"
                )
            ),
            "mediaPlayback" to listOf(
                "media playback" to listOf(
                    "android.permission.MEDIA_CONTENT_CONTROL"
                )
            ),
            "mediaProjection" to listOf(
                "media projection" to listOf(
                    "android.permission.MEDIA_CONTENT_CONTROL"
                )
            ),
            "microphone" to listOf(
                "microphone access" to listOf(
                    "android.permission.RECORD_AUDIO"
                )
            ),
            "phoneCall" to listOf(
                "phone call management" to listOf(
                    "android.permission.MANAGE_OWN_CALLS",
                    "android.permission.READ_PHONE_STATE"
                )
            ),
            "remoteMessaging" to listOf(
                "remote messaging" to listOf(
                    "android.permission.INTERNET"
                )
            ),
            "systemExempted" to listOf(
                "system exempted" to listOf(
                    "android.permission.SCHEDULE_EXACT_ALARM",
                    "android.permission.USE_EXACT_ALARM",
                    "android.permission.DOWNLOAD_WITHOUT_NOTIFICATION",
                    "android.permission.HIDE_OVERLAY_WINDOWS"
                )
            ),
            "specialUse" to listOf(
                "special use" to listOf(
                    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
                )
            )
        )

        private const val MIN_TARGET_SDK = 34
    }

    // Collected declared permissions from the manifest
    private val declaredPermissions = mutableSetOf<String>()
    private var targetSdkVersion: Int = -1
    private val serviceElements = mutableListOf<Pair<Element, XmlContext>>()

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
                val permName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (permName.isNotEmpty()) {
                    declaredPermissions.add(permName)
                }
            }
            TAG_SERVICE -> {
                serviceElements.add(element to context)
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        val effectiveTargetSdk = if (targetSdkVersion >= 0) {
            targetSdkVersion
        } else {
            context.project.targetSdk
        }

        if (effectiveTargetSdk < MIN_TARGET_SDK) {
            serviceElements.clear()
            declaredPermissions.clear()
            targetSdkVersion = -1
            return
        }

        for ((serviceElement, xmlContext) in serviceElements) {
            checkServiceElement(xmlContext, serviceElement)
        }

        serviceElements.clear()
        declaredPermissions.clear()
        targetSdkVersion = -1
    }

    private fun checkServiceElement(context: XmlContext, serviceElement: Element) {
        val foregroundServiceTypeAttr = serviceElement.getAttributeNS(
            ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE
        )

        if (foregroundServiceTypeAttr.isNullOrEmpty()) {
            return
        }

        // The foregroundServiceType can be a pipe-separated list of types
        val types = foregroundServiceTypeAttr.split("|").map { it.trim() }.filter { it.isNotEmpty() }

        for (type in types) {
            val permissionGroups = TYPE_PERMISSION_GROUPS[type] ?: continue

            val missingGroups = mutableListOf<String>()

            for ((groupDescription, permissionsInGroup) in permissionGroups) {
                val hasAnyPermission = permissionsInGroup.any { perm ->
                    declaredPermissions.contains(perm)
                }
                if (!hasAnyPermission) {
                    missingGroups.add(
                        "$groupDescription (one of: ${permissionsInGroup.joinToString(", ")})"
                    )
                }
            }

            if (missingGroups.isNotEmpty()) {
                val serviceName = serviceElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    .ifEmpty { "<unnamed service>" }

                val message = buildString {
                    append("Foreground service type `$type` requires permission(s) that are not declared. ")
                    append("Service: `$serviceName`. ")
                    append("Missing: ")
                    append(missingGroups.joinToString("; "))
                    append(". For targetSdkVersion >= $MIN_TARGET_SDK, missing these permissions will ")
                    append("cause a `SecurityException` when starting the foreground service.")
                }

                val attrNode = serviceElement.getAttributeNodeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
                if (attrNode != null) {
                    context.report(
                        ISSUE,
                        serviceElement,
                        context.getValueLocation(attrNode),
                        message
                    )
                } else {
                    context.report(
                        ISSUE,
                        serviceElement,
                        context.getElementLocation(serviceElement),
                        message
                    )
                }
            }
        }
    }
}