package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

    companion object {
        private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"
        private const val TARGET_SDK_VERSION = 34

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

        // Required permissions per foreground service type (any one group must be fully satisfied)
        // Each inner list is a set of permissions that must ALL be present (AND group).
        // Multiple inner lists represent alternatives (OR between groups).
        private val TYPE_REQUIRED_PERMISSIONS: Map<String, List<List<String>>> = mapOf(
            TYPE_CAMERA to listOf(
                listOf("android.permission.CAMERA")
            ),
            TYPE_CONNECTED_DEVICE to listOf(
                listOf("android.permission.BLUETOOTH_CONNECT"),
                listOf("android.permission.BLUETOOTH_ADVERTISE"),
                listOf("android.permission.BLUETOOTH_SCAN"),
                listOf("android.permission.UWB_RANGING"),
                listOf("android.permission.CHANGE_NETWORK_STATE"),
                listOf("android.permission.CHANGE_WIFI_STATE"),
                listOf("android.permission.CHANGE_WIFI_MULTICAST_STATE"),
                listOf("android.permission.NFC"),
                listOf("android.permission.TRANSMIT_IR")
            ),
            TYPE_DATA_SYNC to listOf(
                listOf("android.permission.INTERNET")
            ),
            TYPE_HEALTH to listOf(
                listOf("android.permission.ACTIVITY_RECOGNITION"),
                listOf("android.permission.BODY_SENSORS"),
                listOf("android.permission.ACCESS_FINE_LOCATION"),
                listOf("android.permission.ACCESS_COARSE_LOCATION")
            ),
            TYPE_LOCATION to listOf(
                listOf("android.permission.ACCESS_FINE_LOCATION"),
                listOf("android.permission.ACCESS_COARSE_LOCATION")
            ),
            TYPE_MEDIA_PLAYBACK to listOf(
                // No extra permissions required beyond FOREGROUND_SERVICE
                listOf()
            ),
            TYPE_MEDIA_PROJECTION to listOf(
                listOf("android.permission.CAPTURE_VIDEO_OUTPUT")
            ),
            TYPE_MICROPHONE to listOf(
                listOf("android.permission.RECORD_AUDIO")
            ),
            TYPE_PHONE_CALL to listOf(
                listOf("android.permission.MANAGE_OWN_CALLS"),
                listOf("android.permission.READ_PHONE_STATE")
            ),
            TYPE_REMOTE_MESSAGING to listOf(
                // No extra permissions required beyond FOREGROUND_SERVICE
                listOf()
            ),
            TYPE_SHORT_SERVICE to listOf(
                // No extra permissions required beyond FOREGROUND_SERVICE
                listOf()
            ),
            TYPE_SPECIAL_USE to listOf(
                // Requires FOREGROUND_SERVICE_SPECIAL_USE in addition
                listOf("android.permission.FOREGROUND_SERVICE_SPECIAL_USE")
            ),
            TYPE_SYSTEM_EXEMPTED to listOf(
                // No extra permissions required beyond FOREGROUND_SERVICE
                listOf()
            )
        )

        // Map of foreground service type to the required FOREGROUND_SERVICE_* permission (Android 14+)
        private val TYPE_FOREGROUND_SERVICE_PERMISSIONS: Map<String, String> = mapOf(
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

        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For `targetSdkVersion` >= $TARGET_SDK_VERSION, each `foregroundServiceType` listed \
                in the `<service>` element requires specific sets of permissions to be declared in \
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
            ),
            androidSpecific = true
        )
    }

    override fun getApplicableElements(): Collection<String> = listOf(TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        // Only check for targetSdkVersion >= 34
        val project = context.project
        val targetSdk = project.targetSdk
        if (targetSdk < TARGET_SDK_VERSION) return

        // Get the foregroundServiceType attribute
        val fgServiceTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
            ?: return
        val fgServiceTypeValue = fgServiceTypeAttr.value
        if (fgServiceTypeValue.isBlank()) return

        // Collect all declared permissions in the manifest
        val declaredPermissions = collectDeclaredPermissions(context)

        // Parse the foreground service types (pipe-separated)
        val serviceTypes = fgServiceTypeValue.split("|").map { it.trim() }.filter { it.isNotEmpty() }

        val missingPermissions = mutableListOf<String>()

        for (serviceType in serviceTypes) {
            // Check the FOREGROUND_SERVICE_* permission for this type (Android 14+)
            val fgServicePermission = TYPE_FOREGROUND_SERVICE_PERMISSIONS[serviceType]
            if (fgServicePermission != null && fgServicePermission !in declaredPermissions) {
                missingPermissions.add(fgServicePermission)
            }

            // Check the required capability permissions for this type
            val requiredPermissionGroups = TYPE_REQUIRED_PERMISSIONS[serviceType]
            if (requiredPermissionGroups != null && requiredPermissionGroups.isNotEmpty()) {
                // Filter out empty groups (types that have no extra requirements)
                val nonEmptyGroups = requiredPermissionGroups.filter { it.isNotEmpty() }
                if (nonEmptyGroups.isNotEmpty()) {
                    // Check if at least one group is fully satisfied
                    val anySatisfied = nonEmptyGroups.any { group ->
                        group.all { permission -> permission in declaredPermissions }
                    }
                    if (!anySatisfied) {
                        // Report the first group's permissions as the minimal requirement
                        // (pick the smallest group, or just the first)
                        val smallestGroup = nonEmptyGroups.minByOrNull { it.size } ?: nonEmptyGroups.first()
                        for (perm in smallestGroup) {
                            if (perm !in declaredPermissions && perm !in missingPermissions) {
                                missingPermissions.add(perm)
                            }
                        }
                    }
                }
            }
        }

        if (missingPermissions.isNotEmpty()) {
            val serviceNameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
            val serviceName = serviceNameAttr?.value ?: "<unknown>"
            val location = context.getValueLocation(fgServiceTypeAttr)
            val permissionList = missingPermissions.joinToString(", ") { "`$it`" }
            context.report(
                ISSUE,
                element,
                location,
                "Service `$serviceName` with foreground service type(s) `$fgServiceTypeValue` " +
                    "requires the following permission(s) to be declared in the manifest: " +
                    "$permissionList. Failing to do so will result in a `SecurityException` " +
                    "when the service is started on Android $TARGET_SDK_VERSION+."
            )
        }
    }

    private fun collectDeclaredPermissions(context: XmlContext): Set<String> {
        val permissions = mutableSetOf<String>()
        val document = context.document
        val manifestElement = document.documentElement ?: return permissions

        val childNodes = manifestElement.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is Element && node.tagName == TAG_USES_PERMISSION) {
                val name = node.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name.isNotEmpty()) {
                    permissions.add(name)
                }
            }
        }
        return permissions
    }
}