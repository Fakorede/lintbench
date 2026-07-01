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
 * Lint detector that checks for missing permissions required by foregroundServiceType
 * for apps targeting Android 14 (API 34) and above.
 */
class ForegroundServicePermissionDetector : Detector(), XmlScanner {

    companion object {
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
            ),
            androidSpecific = true
        )

        // Foreground service type attribute name
        private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"

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

        // Permission constants
        private const val PERMISSION_CAMERA = "android.permission.CAMERA"
        private const val PERMISSION_BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
        private const val PERMISSION_BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
        private const val PERMISSION_BLUETOOTH_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE"
        private const val PERMISSION_CHANGE_NETWORK_STATE = "android.permission.CHANGE_NETWORK_STATE"
        private const val PERMISSION_CHANGE_WIFI_STATE = "android.permission.CHANGE_WIFI_STATE"
        private const val PERMISSION_CHANGE_WIFI_MULTICAST_STATE = "android.permission.CHANGE_WIFI_MULTICAST_STATE"
        private const val PERMISSION_NFC = "android.permission.NFC"
        private const val PERMISSION_TRANSMIT_IR = "android.permission.TRANSMIT_IR"
        private const val PERMISSION_UWB_RANGING = "android.permission.UWB_RANGING"
        private const val PERMISSION_BODY_SENSORS = "android.permission.BODY_SENSORS"
        private const val PERMISSION_ACTIVITY_RECOGNITION = "android.permission.ACTIVITY_RECOGNITION"
        private const val PERMISSION_HIGH_SAMPLING_RATE_SENSORS = "android.permission.HIGH_SAMPLING_RATE_SENSORS"
        private const val PERMISSION_ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
        private const val PERMISSION_ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
        private const val PERMISSION_ACCESS_BACKGROUND_LOCATION = "android.permission.ACCESS_BACKGROUND_LOCATION"
        private const val PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION = "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"
        private const val PERMISSION_RECORD_AUDIO = "android.permission.RECORD_AUDIO"
        private const val PERMISSION_CALL_PHONE = "android.permission.CALL_PHONE"
        private const val PERMISSION_ANSWER_PHONE_CALLS = "android.permission.ANSWER_PHONE_CALLS"
        private const val PERMISSION_MANAGE_OWN_CALLS = "android.permission.MANAGE_OWN_CALLS"
        private const val PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE = "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"

        // Foreground service type permissions: each type requires at least one permission from
        // one of the groups (represented as list of lists - OR between groups, AND within group)
        // Structure: Map<TypeName, List<PermissionGroup>> where PermissionGroup is a Set<String>
        // The service type requires at least ONE permission from AT LEAST ONE group to be satisfied.
        // Actually the requirement is more nuanced per type - let's define it as:
        // Map<TypeName, List<Set<String>>> where the outer list is OR (any group satisfies),
        // and inner Set is AND (all permissions in the group must be present).

        /**
         * For each foreground service type, the required permissions.
         * The structure is: type -> list of permission groups
         * At least one complete permission group must be satisfied.
         * A permission group is satisfied when ALL permissions in the group are declared.
         */
        private val TYPE_REQUIRED_PERMISSION_GROUPS: Map<String, List<Set<String>>> = mapOf(
            TYPE_CAMERA to listOf(
                setOf(PERMISSION_CAMERA)
            ),
            TYPE_CONNECTED_DEVICE to listOf(
                setOf(PERMISSION_BLUETOOTH_SCAN),
                setOf(PERMISSION_BLUETOOTH_CONNECT),
                setOf(PERMISSION_BLUETOOTH_ADVERTISE),
                setOf(PERMISSION_CHANGE_NETWORK_STATE),
                setOf(PERMISSION_CHANGE_WIFI_STATE),
                setOf(PERMISSION_CHANGE_WIFI_MULTICAST_STATE),
                setOf(PERMISSION_NFC),
                setOf(PERMISSION_TRANSMIT_IR),
                setOf(PERMISSION_UWB_RANGING)
            ),
            TYPE_HEALTH to listOf(
                setOf(PERMISSION_BODY_SENSORS),
                setOf(PERMISSION_ACTIVITY_RECOGNITION),
                setOf(PERMISSION_HIGH_SAMPLING_RATE_SENSORS)
            ),
            TYPE_LOCATION to listOf(
                setOf(PERMISSION_ACCESS_FINE_LOCATION),
                setOf(PERMISSION_ACCESS_COARSE_LOCATION)
            ),
            TYPE_MEDIA_PROJECTION to listOf(
                setOf(PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION)
            ),
            TYPE_MICROPHONE to listOf(
                setOf(PERMISSION_RECORD_AUDIO)
            ),
            TYPE_PHONE_CALL to listOf(
                setOf(PERMISSION_CALL_PHONE),
                setOf(PERMISSION_ANSWER_PHONE_CALLS),
                setOf(PERMISSION_MANAGE_OWN_CALLS)
            ),
            TYPE_SPECIAL_USE to listOf(
                setOf(PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE)
            )
            // dataSync, mediaPlayback, remoteMessaging, shortService, systemExempted
            // don't require specific permissions beyond FOREGROUND_SERVICE
        )

        private val TARGET_SDK_REQUIRING_PERMISSIONS = 34
    }

    /** Permissions declared in the manifest */
    private val declaredPermissions = mutableSetOf<String>()

    /** Service elements to check after we've collected all permissions */
    private val servicesToCheck = mutableListOf<Pair<Element, XmlContext>>()

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
                servicesToCheck.add(Pair(element, context))
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (context !is XmlContext) return

        val mainProject = context.mainProject
        val targetSdk = mainProject.targetSdk

        if (targetSdk < TARGET_SDK_REQUIRING_PERMISSIONS) {
            servicesToCheck.clear()
            declaredPermissions.clear()
            return
        }

        for ((serviceElement, xmlContext) in servicesToCheck) {
            checkServiceElement(xmlContext, serviceElement)
        }

        servicesToCheck.clear()
        declaredPermissions.clear()
    }

    private fun checkServiceElement(context: XmlContext, serviceElement: Element) {
        val foregroundServiceTypeAttr = serviceElement.getAttributeNodeNS(
            ANDROID_URI,
            ATTR_FOREGROUND_SERVICE_TYPE
        ) ?: return

        val foregroundServiceTypeValue = foregroundServiceTypeAttr.value ?: return
        if (foregroundServiceTypeValue.isBlank()) return

        // The foregroundServiceType can be a combination of types using '|'
        val types = foregroundServiceTypeValue.split("|").map { it.trim() }.filter { it.isNotEmpty() }

        for (type in types) {
            val requiredPermissionGroups = TYPE_REQUIRED_PERMISSION_GROUPS[type] ?: continue

            // Check if at least one permission group is satisfied
            val isSatisfied = requiredPermissionGroups.any { permissionGroup ->
                permissionGroup.all { permission -> declaredPermissions.contains(permission) }
            }

            if (!isSatisfied) {
                // Build a helpful message
                val permissionsDescription = buildPermissionsDescription(requiredPermissionGroups)
                val serviceName = serviceElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    .let { if (it.isNotEmpty()) " (${it})" else "" }

                context.report(
                    issue = ISSUE,
                    scope = foregroundServiceTypeAttr,
                    location = context.getValueLocation(foregroundServiceTypeAttr),
                    message = "Foreground service type `$type` requires one of the following " +
                            "permissions to be declared in the manifest$serviceName: " +
                            permissionsDescription
                )
            }
        }
    }

    private fun buildPermissionsDescription(permissionGroups: List<Set<String>>): String {
        return if (permissionGroups.size == 1) {
            val group = permissionGroups.first()
            if (group.size == 1) {
                "`${group.first()}`"
            } else {
                group.joinToString(" and ") { "`$it`" }
            }
        } else {
            permissionGroups.joinToString(" or ") { group ->
                if (group.size == 1) {
                    "`${group.first()}`"
                } else {
                    group.joinToString(" and ") { "`$it`" }
                }
            }
        }
    }
}