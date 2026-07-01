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
        private const val MIN_TARGET_SDK = 34

        // Mapping from foregroundServiceType name to required permission sets.
        // Each inner list is an "AND" group — all permissions in the group must be present.
        // Multiple inner lists represent alternatives (OR) — at least one group must be fully satisfied.
        // For simplicity, we model each type as requiring all listed permissions (AND semantics).
        private val TYPE_REQUIRED_PERMISSIONS: Map<String, List<List<String>>> = mapOf(
            "camera" to listOf(
                listOf("android.permission.CAMERA")
            ),
            "connectedDevice" to listOf(
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
            "dataSync" to listOf(
                listOf() // no specific permission required beyond FOREGROUND_SERVICE
            ),
            "health" to listOf(
                listOf("android.permission.ACTIVITY_RECOGNITION"),
                listOf("android.permission.BODY_SENSORS"),
                listOf("android.permission.HIGH_SAMPLING_RATE_SENSORS")
            ),
            "location" to listOf(
                listOf("android.permission.ACCESS_COARSE_LOCATION"),
                listOf("android.permission.ACCESS_FINE_LOCATION")
            ),
            "mediaPlayback" to listOf(
                listOf() // no specific permission required beyond FOREGROUND_SERVICE
            ),
            "mediaProjection" to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION")
            ),
            "microphone" to listOf(
                listOf("android.permission.RECORD_AUDIO")
            ),
            "phoneCall" to listOf(
                listOf("android.permission.MANAGE_OWN_CALLS"),
                listOf("android.permission.READ_PHONE_STATE")
            ),
            "remoteMessaging" to listOf(
                listOf() // no specific permission required beyond FOREGROUND_SERVICE
            ),
            "shortService" to listOf(
                listOf() // no specific permission required beyond FOREGROUND_SERVICE
            ),
            "specialUse" to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_SPECIAL_USE")
            ),
            "systemExempted" to listOf(
                listOf() // system-only; no user-declarable permission
            )
        )

        // For Android 14+, each foreground service type also requires the corresponding
        // android.permission.FOREGROUND_SERVICE_* permission.
        private val TYPE_FOREGROUND_SERVICE_PERMISSION: Map<String, String> = mapOf(
            "camera" to "android.permission.FOREGROUND_SERVICE_CAMERA",
            "connectedDevice" to "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
            "dataSync" to "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            "health" to "android.permission.FOREGROUND_SERVICE_HEALTH",
            "location" to "android.permission.FOREGROUND_SERVICE_LOCATION",
            "mediaPlayback" to "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            "mediaProjection" to "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
            "microphone" to "android.permission.FOREGROUND_SERVICE_MICROPHONE",
            "phoneCall" to "android.permission.FOREGROUND_SERVICE_PHONE_CALL",
            "remoteMessaging" to "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING",
            "shortService" to "android.permission.FOREGROUND_SERVICE_SHORT_SERVICE",
            "specialUse" to "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            "systemExempted" to "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For `targetSdkVersion` >= 34 (Android 14), each `foregroundServiceType` declared \
                in a `<service>` element requires specific permissions to be declared in the \
                manifest. If the required permissions are missing, a `SecurityException` will be \
                thrown at runtime when the foreground service is started with that type.

                Each foreground service type requires both:
                1. The `android.permission.FOREGROUND_SERVICE_*` permission corresponding to the \
                   type.
                2. Any runtime permissions associated with the data/hardware the service accesses \
                   (e.g., `CAMERA` for the `camera` type, `RECORD_AUDIO` for `microphone`, etc.).

                Ensure all required permissions are declared in your manifest.
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
        // Only check when targetSdkVersion >= 34
        val project = context.project
        val targetSdk = project.targetSdk
        if (targetSdk < MIN_TARGET_SDK) return

        // Read the foregroundServiceType attribute
        val typeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
            ?: return
        val typeValue = typeAttr.value.trim()
        if (typeValue.isEmpty()) return

        // Collect all declared permissions in the manifest
        val declaredPermissions = collectDeclaredPermissions(element)

        // foregroundServiceType can be a pipe-separated list of types
        val types = typeValue.split("|").map { it.trim() }.filter { it.isNotEmpty() }

        for (type in types) {
            val missingPermissions = mutableListOf<String>()

            // Check the FOREGROUND_SERVICE_* permission for this type (Android 14 requirement)
            val fgsPermission = TYPE_FOREGROUND_SERVICE_PERMISSION[type]
            if (fgsPermission != null && fgsPermission !in declaredPermissions) {
                missingPermissions.add(fgsPermission)
            }

            // Check that at least one of the required permission groups is satisfied.
            // Each group in the list is an AND-group; we require at least one group to be fully present.
            val requiredGroups = TYPE_REQUIRED_PERMISSIONS[type]
            if (requiredGroups != null) {
                // Filter out empty groups (types with no extra requirement)
                val nonEmptyGroups = requiredGroups.filter { it.isNotEmpty() }
                if (nonEmptyGroups.isNotEmpty()) {
                    // Check if at least one group is fully satisfied
                    val anyGroupSatisfied = nonEmptyGroups.any { group ->
                        group.all { perm -> perm in declaredPermissions }
                    }
                    if (!anyGroupSatisfied) {
                        // Report the permissions from the first group as an example
                        val firstGroup = nonEmptyGroups.first()
                        for (perm in firstGroup) {
                            if (perm !in declaredPermissions && perm !in missingPermissions) {
                                missingPermissions.add(perm)
                            }
                        }
                    }
                }
            }

            if (missingPermissions.isNotEmpty()) {
                val serviceNameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
                val serviceName = serviceNameAttr?.value ?: "<unknown>"
                val location = context.getValueLocation(typeAttr)
                val permList = missingPermissions.joinToString(", ") { "`$it`" }
                context.report(
                    ISSUE,
                    element,
                    location,
                    "Foreground service type `$type` for service `$serviceName` requires " +
                        "the following permission(s) to be declared: $permList. " +
                        "Without these, a `SecurityException` will be thrown on Android 14+ " +
                        "when starting this foreground service."
                )
            }
        }
    }

    /**
     * Collects all permission names declared via <uses-permission> in the manifest document
     * that contains the given element.
     */
    private fun collectDeclaredPermissions(element: Element): Set<String> {
        val document = element.ownerDocument ?: return emptySet()
        val root = document.documentElement ?: return emptySet()
        val permissions = mutableSetOf<String>()

        val permissionNodes = root.getElementsByTagName(TAG_USES_PERMISSION)
        for (i in 0 until permissionNodes.length) {
            val node = permissionNodes.item(i) as? Element ?: continue
            val name = node.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name.isNotEmpty()) {
                permissions.add(name)
            }
        }

        return permissions
    }
}