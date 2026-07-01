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

        // Foreground service type names as used in the manifest attribute
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
        private const val PERM_BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
        private const val PERM_BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
        private const val PERM_BLUETOOTH_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE"
        private const val PERM_CHANGE_NETWORK_STATE = "android.permission.CHANGE_NETWORK_STATE"
        private const val PERM_CHANGE_WIFI_STATE = "android.permission.CHANGE_WIFI_STATE"
        private const val PERM_CHANGE_WIFI_MULTICAST_STATE = "android.permission.CHANGE_WIFI_MULTICAST_STATE"
        private const val PERM_NFC = "android.permission.NFC"
        private const val PERM_TRANSMIT_IR = "android.permission.TRANSMIT_IR"
        private const val PERM_UWB_RANGING = "android.permission.UWB_RANGING"
        private const val PERM_ACTIVITY_RECOGNITION = "android.permission.ACTIVITY_RECOGNITION"
        private const val PERM_BODY_SENSORS = "android.permission.BODY_SENSORS"
        private const val PERM_ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
        private const val PERM_ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
        private const val PERM_RECORD_AUDIO = "android.permission.RECORD_AUDIO"
        private const val PERM_ACCEPT_HANDOVER = "android.permission.ACCEPT_HANDOVER"
        private const val PERM_MANAGE_OWN_CALLS = "android.permission.MANAGE_OWN_CALLS"
        private const val PERM_READ_PHONE_NUMBERS = "android.permission.READ_PHONE_NUMBERS"
        private const val PERM_READ_PHONE_STATE = "android.permission.READ_PHONE_STATE"
        private const val PERM_RECEIVE_MMS = "android.permission.RECEIVE_MMS"
        private const val PERM_RECEIVE_SMS = "android.permission.RECEIVE_SMS"
        private const val PERM_RECEIVE_WAP_PUSH = "android.permission.RECEIVE_WAP_PUSH"
        private const val PERM_SEND_SMS = "android.permission.SEND_SMS"
        private const val PERM_READ_SMS = "android.permission.READ_SMS"

        /**
         * For each foreground service type, returns the list of permission sets.
         * The service type requires at least one permission from each inner list (OR within group,
         * AND between groups). For simplicity, we report if NONE of the permissions in any group
         * are present.
         *
         * Structure: Map<typeName, List<Set<permission>>> where each Set is an OR-group
         * (at least one from the set must be declared) and all sets must be satisfied.
         */
        private val TYPE_REQUIRED_PERMISSIONS: Map<String, List<Set<String>>> = mapOf(
            TYPE_CAMERA to listOf(
                setOf(PERM_FOREGROUND_SERVICE_CAMERA),
                setOf(PERM_CAMERA)
            ),
            TYPE_CONNECTED_DEVICE to listOf(
                setOf(PERM_FOREGROUND_SERVICE_CONNECTED_DEVICE),
                setOf(
                    PERM_BLUETOOTH_CONNECT,
                    PERM_BLUETOOTH_SCAN,
                    PERM_BLUETOOTH_ADVERTISE,
                    PERM_CHANGE_NETWORK_STATE,
                    PERM_CHANGE_WIFI_STATE,
                    PERM_CHANGE_WIFI_MULTICAST_STATE,
                    PERM_NFC,
                    PERM_TRANSMIT_IR,
                    PERM_UWB_RANGING
                )
            ),
            TYPE_DATA_SYNC to listOf(
                setOf(PERM_FOREGROUND_SERVICE_DATA_SYNC)
            ),
            TYPE_HEALTH to listOf(
                setOf(PERM_FOREGROUND_SERVICE_HEALTH),
                setOf(
                    PERM_ACTIVITY_RECOGNITION,
                    PERM_BODY_SENSORS,
                    PERM_ACCESS_FINE_LOCATION,
                    PERM_ACCESS_COARSE_LOCATION
                )
            ),
            TYPE_LOCATION to listOf(
                setOf(PERM_FOREGROUND_SERVICE_LOCATION),
                setOf(PERM_ACCESS_FINE_LOCATION, PERM_ACCESS_COARSE_LOCATION)
            ),
            TYPE_MEDIA_PLAYBACK to listOf(
                setOf(PERM_FOREGROUND_SERVICE_MEDIA_PLAYBACK)
            ),
            TYPE_MEDIA_PROJECTION to listOf(
                setOf(PERM_FOREGROUND_SERVICE_MEDIA_PROJECTION)
            ),
            TYPE_MICROPHONE to listOf(
                setOf(PERM_FOREGROUND_SERVICE_MICROPHONE),
                setOf(PERM_RECORD_AUDIO)
            ),
            TYPE_PHONE_CALL to listOf(
                setOf(PERM_FOREGROUND_SERVICE_PHONE_CALL),
                setOf(
                    PERM_ACCEPT_HANDOVER,
                    PERM_MANAGE_OWN_CALLS,
                    PERM_READ_PHONE_NUMBERS,
                    PERM_READ_PHONE_STATE
                )
            ),
            TYPE_REMOTE_MESSAGING to listOf(
                setOf(PERM_FOREGROUND_SERVICE_REMOTE_MESSAGING),
                setOf(
                    PERM_RECEIVE_MMS,
                    PERM_RECEIVE_SMS,
                    PERM_RECEIVE_WAP_PUSH,
                    PERM_SEND_SMS,
                    PERM_READ_SMS
                )
            ),
            TYPE_SHORT_SERVICE to listOf(
                setOf(PERM_FOREGROUND_SERVICE_SHORT_SERVICE)
            ),
            TYPE_SPECIAL_USE to listOf(
                setOf(PERM_FOREGROUND_SERVICE_SPECIAL_USE)
            ),
            TYPE_SYSTEM_EXEMPTED to listOf(
                setOf(PERM_FOREGROUND_SERVICE_SYSTEM_EXEMPTED)
            )
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For `targetSdkVersion` >= $MIN_TARGET_SDK, each `foregroundServiceType` listed \
                in the `<service>` element requires specific permissions to be declared in the \
                manifest. If the required permissions are missing, a `SecurityException` will \
                be thrown when the foreground service is started with that type.

                Each foreground service type requires the corresponding \
                `FOREGROUND_SERVICE_*` permission, plus at least one of the associated \
                capability-specific permissions.
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
        // Only applicable for targetSdkVersion >= 34
        val project = context.project
        val targetSdk = project.targetSdk
        if (targetSdk < MIN_TARGET_SDK) return

        // Get the foregroundServiceType attribute
        val fstAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
            ?: return
        val fstValue = fstAttr.value.trim()
        if (fstValue.isEmpty()) return

        // Collect all declared permissions in the manifest
        val declaredPermissions = collectDeclaredPermissions(context)

        // The foregroundServiceType can be a '|'-separated list of types
        val types = fstValue.split("|").map { it.trim() }.filter { it.isNotEmpty() }

        for (type in types) {
            val requiredGroups = TYPE_REQUIRED_PERMISSIONS[type] ?: continue

            val missingGroups = mutableListOf<Set<String>>()
            for (group in requiredGroups) {
                val hasOne = group.any { perm -> declaredPermissions.contains(perm) }
                if (!hasOne) {
                    missingGroups.add(group)
                }
            }

            if (missingGroups.isNotEmpty()) {
                val missingDesc = missingGroups.joinToString(" AND ") { group ->
                    if (group.size == 1) {
                        group.first()
                    } else {
                        "(one of: ${group.joinToString(", ")})"
                    }
                }
                val message = "Foreground service type `$type` requires the following " +
                    "permission(s) to be declared: $missingDesc. " +
                    "A `SecurityException` will be thrown if these are missing on API $MIN_TARGET_SDK+."
                context.report(
                    ISSUE,
                    element,
                    context.getValueLocation(fstAttr),
                    message
                )
            }
        }
    }

    private fun collectDeclaredPermissions(context: XmlContext): Set<String> {
        val permissions = mutableSetOf<String>()
        val document = context.document
        val manifestElement = document.documentElement ?: return permissions

        // Walk through all <uses-permission> elements at manifest level
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