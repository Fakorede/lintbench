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
         * For each foreground service type, the list of required permission sets.
         * Each inner list is an "OR" group — at least one permission from the group must be present.
         * The outer list is "AND" — all groups must be satisfied.
         */
        private val TYPE_REQUIRED_PERMISSIONS: Map<String, List<List<String>>> = mapOf(
            TYPE_CAMERA to listOf(
                listOf(PERM_FOREGROUND_SERVICE_CAMERA),
                listOf(PERM_CAMERA)
            ),
            TYPE_CONNECTED_DEVICE to listOf(
                listOf(PERM_FOREGROUND_SERVICE_CONNECTED_DEVICE)
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
            ),
            androidSpecific = true
        )
    }

    override fun getApplicableElements(): Collection<String> = listOf(TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        // Only apply for targetSdkVersion >= 34
        val targetSdk = context.project.targetSdk
        if (targetSdk < MIN_TARGET_SDK) return

        // Get the foregroundServiceType attribute
        val fgServiceTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
            ?: return
        val fgServiceTypeValue = fgServiceTypeAttr.value
        if (fgServiceTypeValue.isBlank()) return

        // Parse the pipe-separated list of types
        val declaredTypes = fgServiceTypeValue.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        if (declaredTypes.isEmpty()) return

        // Collect all declared permissions in the manifest
        val declaredPermissions = collectDeclaredPermissions(context)

        // Always require android.permission.FOREGROUND_SERVICE
        if (PERM_FOREGROUND_SERVICE !in declaredPermissions) {
            context.report(
                ISSUE,
                element,
                context.getValueLocation(fgServiceTypeAttr),
                "Missing required permission `$PERM_FOREGROUND_SERVICE` for foreground service"
            )
        }

        // Check each declared foreground service type
        for (type in declaredTypes) {
            val requiredPermissionGroups = TYPE_REQUIRED_PERMISSIONS[type] ?: continue

            val missingGroups = mutableListOf<List<String>>()
            for (group in requiredPermissionGroups) {
                val groupSatisfied = group.any { it in declaredPermissions }
                if (!groupSatisfied) {
                    missingGroups.add(group)
                }
            }

            if (missingGroups.isNotEmpty()) {
                val missingDescription = missingGroups.joinToString(separator = " and ") { group ->
                    if (group.size == 1) {
                        "`${group[0]}`"
                    } else {
                        "one of ${group.joinToString(", ") { "`$it`" }}"
                    }
                }
                context.report(
                    ISSUE,
                    element,
                    context.getValueLocation(fgServiceTypeAttr),
                    "Foreground service type `$type` requires the permission(s) $missingDescription " +
                        "to be declared in the manifest; missing these will result in a " +
                        "`SecurityException` on Android 14 (API 34) and higher"
                )
            }
        }
    }

    /**
     * Collects all permissions declared via <uses-permission> in the manifest document.
     */
    private fun collectDeclaredPermissions(context: XmlContext): Set<String> {
        val document = context.document ?: return emptySet()
        val manifestElement = document.documentElement ?: return emptySet()

        val permissions = mutableSetOf<String>()
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