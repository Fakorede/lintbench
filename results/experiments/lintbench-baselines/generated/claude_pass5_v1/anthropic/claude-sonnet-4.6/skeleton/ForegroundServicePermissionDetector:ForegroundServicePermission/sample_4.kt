package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
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
        private const val TAG_MANIFEST = "manifest"
        private const val TAG_USES_SDK = "uses-sdk"
        private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"
        private const val ATTR_TARGET_SDK_VERSION = "targetSdkVersion"
        private const val ATTR_NAME = "name"

        // Foreground service type values (bitmask values from Android source)
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

        // Required permissions for each foreground service type (at least one group must be satisfied)
        // Each inner list represents a set of permissions where ALL must be present (AND),
        // and the outer list represents alternatives where ANY can satisfy (OR).
        // We model this as: List of permission groups, where at least one group must be fully declared.
        // For simplicity, we store required permissions as sets that ALL must be present.
        private val REQUIRED_PERMISSIONS: Map<String, List<Set<String>>> = mapOf(
            TYPE_DATA_SYNC to listOf(
                setOf("android.permission.FOREGROUND_SERVICE_DATA_SYNC")
            ),
            TYPE_MEDIA_PLAYBACK to listOf(
                setOf("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK")
            ),
            TYPE_PHONE_CALL to listOf(
                setOf(
                    "android.permission.FOREGROUND_SERVICE_PHONE_CALL",
                    "android.permission.MANAGE_OWN_CALLS"
                ),
                setOf(
                    "android.permission.FOREGROUND_SERVICE_PHONE_CALL",
                    "android.permission.READ_PHONE_NUMBERS"
                ),
                setOf(
                    "android.permission.FOREGROUND_SERVICE_PHONE_CALL",
                    "android.permission.READ_PHONE_STATE"
                )
            ),
            TYPE_LOCATION to listOf(
                setOf(
                    "android.permission.FOREGROUND_SERVICE_LOCATION",
                    "android.permission.ACCESS_COARSE_LOCATION"
                ),
                setOf(
                    "android.permission.FOREGROUND_SERVICE_LOCATION",
                    "android.permission.ACCESS_FINE_LOCATION"
                )
            ),
            TYPE_CONNECTED_DEVICE to listOf(
                setOf(
                    "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
                    "android.permission.BLUETOOTH_ADVERTISE"
                ),
                setOf(
                    "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
                    "android.permission.BLUETOOTH_CONNECT"
                ),
                setOf(
                    "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
                    "android.permission.BLUETOOTH_SCAN"
                ),
                setOf(
                    "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
                    "android.permission.CHANGE_NETWORK_STATE"
                ),
                setOf(
                    "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
                    "android.permission.CHANGE_WIFI_MULTICAST_STATE"
                ),
                setOf(
                    "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
                    "android.permission.CHANGE_WIFI_STATE"
                ),
                setOf(
                    "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
                    "android.permission.NFC"
                ),
                setOf(
                    "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
                    "android.permission.TRANSMIT_IR"
                ),
                setOf(
                    "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
                    "android.permission.UWB_RANGING"
                )
            ),
            TYPE_MEDIA_PROJECTION to listOf(
                setOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION")
            ),
            TYPE_CAMERA to listOf(
                setOf(
                    "android.permission.FOREGROUND_SERVICE_CAMERA",
                    "android.permission.CAMERA"
                )
            ),
            TYPE_MICROPHONE to listOf(
                setOf(
                    "android.permission.FOREGROUND_SERVICE_MICROPHONE",
                    "android.permission.CAPTURE_AUDIO_OUTPUT"
                ),
                setOf(
                    "android.permission.FOREGROUND_SERVICE_MICROPHONE",
                    "android.permission.RECORD_AUDIO"
                )
            ),
            TYPE_HEALTH to listOf(
                setOf(
                    "android.permission.FOREGROUND_SERVICE_HEALTH",
                    "android.permission.ACTIVITY_RECOGNITION"
                ),
                setOf(
                    "android.permission.FOREGROUND_SERVICE_HEALTH",
                    "android.permission.BODY_SENSORS"
                ),
                setOf(
                    "android.permission.FOREGROUND_SERVICE_HEALTH",
                    "android.permission.HIGH_SAMPLING_RATE_SENSORS"
                )
            ),
            TYPE_REMOTE_MESSAGING to listOf(
                setOf("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING")
            ),
            TYPE_SYSTEM_EXEMPTED to listOf(
                setOf("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED")
            ),
            TYPE_SHORT_SERVICE to listOf(
                setOf("android.permission.FOREGROUND_SERVICE_SHORT_SERVICE")
            ),
            TYPE_FILE_MANAGEMENT to listOf(
                setOf("android.permission.FOREGROUND_SERVICE_FILE_MANAGEMENT")
            ),
            TYPE_SPECIAL_USE to listOf(
                setOf("android.permission.FOREGROUND_SERVICE_SPECIAL_USE")
            )
        )

        private const val MIN_TARGET_SDK = 34
    }

    override fun getApplicableElements(): Collection<String> = listOf(TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        // Only check for targetSdkVersion >= 34
        val targetSdkVersion = getTargetSdkVersion(context, element)
        if (targetSdkVersion < MIN_TARGET_SDK) {
            return
        }

        // Get foregroundServiceType attribute
        val foregroundServiceTypeAttr = element.getAttributeNodeNS(
            SdkConstants.ANDROID_URI,
            ATTR_FOREGROUND_SERVICE_TYPE
        ) ?: return

        val foregroundServiceTypeValue = foregroundServiceTypeAttr.value
        if (foregroundServiceTypeValue.isBlank()) return

        // Parse foreground service types (can be pipe-separated)
        val serviceTypes = foregroundServiceTypeValue.split("|").map { it.trim() }.filter { it.isNotEmpty() }

        // Collect all declared permissions in the manifest
        val declaredPermissions = getDeclaredPermissions(context, element)

        // Check each service type
        for (serviceType in serviceTypes) {
            val requiredPermissionGroups = REQUIRED_PERMISSIONS[serviceType] ?: continue

            // Check if at least one group is fully satisfied
            val isSatisfied = requiredPermissionGroups.any { permissionGroup ->
                permissionGroup.all { permission -> declaredPermissions.contains(permission) }
            }

            if (!isSatisfied) {
                // Find the best group to suggest (the one with most permissions already declared)
                val bestGroup = requiredPermissionGroups.maxByOrNull { group ->
                    group.count { permission -> declaredPermissions.contains(permission) }
                } ?: requiredPermissionGroups.first()

                val missingPermissions = bestGroup.filter { permission ->
                    !declaredPermissions.contains(permission)
                }

                val permissionList = missingPermissions.joinToString(", ") { "`$it`" }
                val message = if (missingPermissions.size == 1) {
                    "Foreground service type `$serviceType` requires the permission $permissionList"
                } else {
                    "Foreground service type `$serviceType` requires the permissions $permissionList"
                }

                context.report(
                    ISSUE,
                    element,
                    context.getLocation(foregroundServiceTypeAttr),
                    message
                )
            }
        }
    }

    /**
     * Collects all permissions declared with <uses-permission> in the manifest.
     */
    private fun getDeclaredPermissions(context: XmlContext, element: Element): Set<String> {
        val permissions = mutableSetOf<String>()

        // Walk up to find the manifest root element
        val manifestElement = findManifestElement(element) ?: return permissions

        // Collect all uses-permission elements
        val childNodes = manifestElement.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                if (child.tagName == TAG_USES_PERMISSION ||
                    child.tagName == TAG_USES_PERMISSION_SDK_23
                ) {
                    val permName = child.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_NAME)
                    if (permName.isNotEmpty()) {
                        permissions.add(permName)
                    }
                }
            }
        }

        return permissions
    }

    /**
     * Finds the root <manifest> element by walking up the DOM tree.
     */
    private fun findManifestElement(element: Element): Element? {
        var node: org.w3c.dom.Node? = element
        while (node != null) {
            if (node is Element && node.tagName == TAG_MANIFEST) {
                return node
            }
            node = node.parentNode
        }
        return null
    }

    /**
     * Gets the targetSdkVersion from the manifest.
     */
    private fun getTargetSdkVersion(context: XmlContext, element: Element): Int {
        val manifestElement = findManifestElement(element) ?: return 0

        val childNodes = manifestElement.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element && child.tagName == TAG_USES_SDK) {
                val targetSdkStr = child.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_TARGET_SDK_VERSION)
                if (targetSdkStr.isNotEmpty()) {
                    return targetSdkStr.toIntOrNull() ?: 0
                }
            }
        }

        // Fall back to the project's target SDK if available
        val project = context.project
        return project.targetSdk
    }
}