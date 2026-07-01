package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_TARGET_SDK_VERSION
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
import org.w3c.dom.NodeList

class ForegroundServicePermissionDetector : Detector(), XmlScanner {

    companion object {
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
                Scope.MANIFEST_SCOPE
            )
        )

        // foregroundServiceType attribute name
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

        // Android permission constants
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
        private const val PERM_ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
        private const val PERM_ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
        private const val PERM_RECORD_AUDIO = "android.permission.RECORD_AUDIO"
        private const val PERM_MANAGE_OWN_CALLS = "android.permission.MANAGE_OWN_CALLS"
        private const val PERM_READ_PHONE_NUMBERS = "android.permission.READ_PHONE_NUMBERS"
        private const val PERM_READ_PHONE_STATE = "android.permission.READ_PHONE_STATE"

        /**
         * For each foreground service type, defines the required permissions.
         * The outer list is AND (all groups required), inner list is OR (any one from group is sufficient).
         */
        private val TYPE_REQUIRED_PERMISSIONS: Map<String, List<List<String>>> = mapOf(
            TYPE_CAMERA to listOf(
                listOf(PERM_FOREGROUND_SERVICE_CAMERA),
                listOf(PERM_CAMERA)
            ),
            TYPE_CONNECTED_DEVICE to listOf(
                listOf(PERM_FOREGROUND_SERVICE_CONNECTED_DEVICE)
                // connectedDevice requires one of many possible permissions (BT, USB, etc.)
                // but the type-specific permission is the main required one for lint
            ),
            TYPE_DATA_SYNC to listOf(
                listOf(PERM_FOREGROUND_SERVICE_DATA_SYNC)
            ),
            TYPE_HEALTH to listOf(
                listOf(PERM_FOREGROUND_SERVICE_HEALTH)
            ),
            TYPE_LOCATION to listOf(
                listOf(PERM_FOREGROUND_SERVICE_LOCATION),
                listOf(PERM_ACCESS_FINE_LOCATION, PERM_ACCESS_COARSE_LOCATION)
            ),
            TYPE_MEDIA_PLAYBACK to listOf(
                listOf(PERM_FOREGROUND_SERVICE_MEDIA_PLAYBACK)
            ),
            TYPE_MEDIA_PROJECTION to listOf(
                listOf(PERM_FOREGROUND_SERVICE_MEDIA_PROJECTION)
            ),
            TYPE_MICROPHONE to listOf(
                listOf(PERM_FOREGROUND_SERVICE_MICROPHONE),
                listOf(PERM_RECORD_AUDIO)
            ),
            TYPE_PHONE_CALL to listOf(
                listOf(PERM_FOREGROUND_SERVICE_PHONE_CALL),
                listOf(PERM_MANAGE_OWN_CALLS, PERM_READ_PHONE_NUMBERS, PERM_READ_PHONE_STATE)
            ),
            TYPE_REMOTE_MESSAGING to listOf(
                listOf(PERM_FOREGROUND_SERVICE_REMOTE_MESSAGING)
            ),
            TYPE_SHORT_SERVICE to listOf(
                listOf(PERM_FOREGROUND_SERVICE_SHORT_SERVICE)
            ),
            TYPE_SPECIAL_USE to listOf(
                listOf(PERM_FOREGROUND_SERVICE_SPECIAL_USE)
            ),
            TYPE_SYSTEM_EXEMPTED to listOf(
                listOf(PERM_FOREGROUND_SERVICE_SYSTEM_EXEMPTED)
            )
        )

        // The type-specific permission for each type (for simpler checking)
        private val TYPE_TO_PERMISSION = mapOf(
            TYPE_CAMERA to PERM_FOREGROUND_SERVICE_CAMERA,
            TYPE_CONNECTED_DEVICE to PERM_FOREGROUND_SERVICE_CONNECTED_DEVICE,
            TYPE_DATA_SYNC to PERM_FOREGROUND_SERVICE_DATA_SYNC,
            TYPE_HEALTH to PERM_FOREGROUND_SERVICE_HEALTH,
            TYPE_LOCATION to PERM_FOREGROUND_SERVICE_LOCATION,
            TYPE_MEDIA_PLAYBACK to PERM_FOREGROUND_SERVICE_MEDIA_PLAYBACK,
            TYPE_MEDIA_PROJECTION to PERM_FOREGROUND_SERVICE_MEDIA_PROJECTION,
            TYPE_MICROPHONE to PERM_FOREGROUND_SERVICE_MICROPHONE,
            TYPE_PHONE_CALL to PERM_FOREGROUND_SERVICE_PHONE_CALL,
            TYPE_REMOTE_MESSAGING to PERM_FOREGROUND_SERVICE_REMOTE_MESSAGING,
            TYPE_SHORT_SERVICE to PERM_FOREGROUND_SERVICE_SHORT_SERVICE,
            TYPE_SPECIAL_USE to PERM_FOREGROUND_SERVICE_SPECIAL_USE,
            TYPE_SYSTEM_EXEMPTED to PERM_FOREGROUND_SERVICE_SYSTEM_EXEMPTED
        )

        private const val MIN_TARGET_SDK = 34
    }

    override fun getApplicableElements(): Collection<String> = listOf(TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        // Only check for targetSdkVersion >= 34
        val targetSdk = getTargetSdkVersion(context)
        if (targetSdk < MIN_TARGET_SDK) {
            return
        }

        // Get the foregroundServiceType attribute
        val foregroundServiceTypeAttr = element.getAttributeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
        if (foregroundServiceTypeAttr.isNullOrEmpty()) {
            return
        }

        // Collect declared permissions from the manifest
        val declaredPermissions = getDeclaredPermissions(context)

        // Parse the foregroundServiceType value (can be multiple types separated by |)
        val serviceTypes = foregroundServiceTypeAttr.split("|").map { it.trim() }.filter { it.isNotEmpty() }

        for (serviceType in serviceTypes) {
            // Strip any namespace prefix if present
            val cleanType = serviceType.substringAfterLast(".")

            checkServiceTypePermissions(context, element, cleanType, declaredPermissions)
        }
    }

    private fun checkServiceTypePermissions(
        context: XmlContext,
        element: Element,
        serviceType: String,
        declaredPermissions: Set<String>
    ) {
        val requiredPermissionGroups = TYPE_REQUIRED_PERMISSIONS[serviceType] ?: return

        val missingGroups = mutableListOf<List<String>>()

        for (permissionGroup in requiredPermissionGroups) {
            // Check if at least one permission from this group is declared
            val hasPermission = permissionGroup.any { perm -> declaredPermissions.contains(perm) }
            if (!hasPermission) {
                missingGroups.add(permissionGroup)
            }
        }

        if (missingGroups.isNotEmpty()) {
            val missingDescriptions = missingGroups.map { group ->
                if (group.size == 1) {
                    group[0]
                } else {
                    "one of [${group.joinToString(", ")}]"
                }
            }

            val serviceName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                .let { if (it.isNotEmpty()) " ($it)" else "" }

            val message = buildString {
                append("Foreground service type `$serviceType`$serviceName requires ")
                if (missingDescriptions.size == 1) {
                    append("the permission `${missingDescriptions[0]}` to be declared in the manifest")
                } else {
                    append("the following permissions to be declared in the manifest: ")
                    append(missingDescriptions.joinToString(" and ") { "`$it`" })
                }
            }

            val attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
            if (attr != null) {
                context.report(
                    ISSUE,
                    element,
                    context.getValueLocation(attr),
                    message
                )
            } else {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    message
                )
            }
        }
    }

    private fun getDeclaredPermissions(context: XmlContext): Set<String> {
        val permissions = mutableSetOf<String>()
        val document = context.document
        val manifestElements = document.getElementsByTagName("manifest")
        if (manifestElements.length == 0) return permissions

        val manifest = manifestElements.item(0) as? Element ?: return permissions
        val children: NodeList = manifest.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            if (node.tagName == TAG_USES_PERMISSION ||
                node.localName == TAG_USES_PERMISSION
            ) {
                val name = node.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name.isNotEmpty()) {
                    permissions.add(name)
                }
            }
        }
        return permissions
    }

    private fun getTargetSdkVersion(context: XmlContext): Int {
        val document = context.document
        val usesSdkElements = document.getElementsByTagName("uses-sdk")
        if (usesSdkElements.length > 0) {
            val usesSdk = usesSdkElements.item(0) as? Element
            if (usesSdk != null) {
                val targetSdk = usesSdk.getAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION)
                if (targetSdk.isNotEmpty()) {
                    return targetSdk.toIntOrNull() ?: 0
                }
            }
        }

        // Fall back to project's target SDK
        val project = context.project
        val targetSdk = project.targetSdk
        if (targetSdk > 0) {
            return targetSdk
        }

        return 0
    }
}