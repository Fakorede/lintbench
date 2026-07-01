package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import java.util.EnumSet
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : ResourceXmlDetector() {

    companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val TAG_SERVICE = "service"
        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val TAG_USES_PERMISSION_SDK_23 = "uses-permission-sdk-23"
        private const val ATTR_NAME = "name"
        private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"

        private val IMPLEMENTATION = Implementation(
            ForegroundServicePermissionDetector::class.java,
            EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE)
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = "On Android 14+ (targetSdkVersion 34 and above), every foregroundServiceType declared on a <service> element requires a matching set of permissions in the manifest. If the required permissions are missing, starting the service with that type will throw a SecurityException.",
            moreInfo = "https://developer.android.com/about/versions/14/changes/fgs-types-required",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )

        private data class PermissionRequirement(
            val permissions: Set<String>,
            val requireAll: Boolean
        )

        private val TYPE_TO_REQUIREMENTS: Map<String, List<PermissionRequirement>> = mapOf(
            "camera" to listOf(
                PermissionRequirement(setOf("android.permission.FOREGROUND_SERVICE_CAMERA"), true),
                PermissionRequirement(setOf("android.permission.CAMERA"), true)
            ),
            "connectedDevice" to listOf(
                PermissionRequirement(setOf("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"), true),
                PermissionRequirement(
                    setOf(
                        "android.permission.BLUETOOTH_CONNECT",
                        "android.permission.BLUETOOTH_SCAN",
                        "android.permission.BLUETOOTH_ADVERTISE",
                        "android.permission.UWB_RANGING"
                    ),
                    false
                )
            ),
            "dataSync" to listOf(
                PermissionRequirement(setOf("android.permission.FOREGROUND_SERVICE_DATA_SYNC"), true)
            ),
            "health" to listOf(
                PermissionRequirement(setOf("android.permission.FOREGROUND_SERVICE_HEALTH"), true),
                PermissionRequirement(
                    setOf(
                        "android.permission.BODY_SENSORS",
                        "android.permission.HIGH_SAMPLING_RATE_SENSORS"
                    ),
                    false
                )
            ),
            "location" to listOf(
                PermissionRequirement(setOf("android.permission.FOREGROUND_SERVICE_LOCATION"), true),
                PermissionRequirement(
                    setOf(
                        "android.permission.ACCESS_COARSE_LOCATION",
                        "android.permission.ACCESS_FINE_LOCATION"
                    ),
                    false
                )
            ),
            "mediaPlayback" to listOf(
                PermissionRequirement(setOf("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"), true)
            ),
            "mediaProjection" to listOf(
                PermissionRequirement(setOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"), true)
            ),
            "microphone" to listOf(
                PermissionRequirement(setOf("android.permission.FOREGROUND_SERVICE_MICROPHONE"), true),
                PermissionRequirement(setOf("android.permission.RECORD_AUDIO"), true)
            ),
            "phoneCall" to listOf(
                PermissionRequirement(setOf("android.permission.FOREGROUND_SERVICE_PHONE_CALL"), true),
                PermissionRequirement(
                    setOf(
                        "android.permission.CALL_PHONE",
                        "android.permission.MANAGE_OWN_CALLS"
                    ),
                    false
                )
            ),
            "specialUse" to listOf(
                PermissionRequirement(setOf("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"), true)
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.mainProject.targetSdkVersion.apiLevel < 34) return

        val attr = element.getAttributeNodeNS(ANDROID_NS, ATTR_FOREGROUND_SERVICE_TYPE) ?: return
        val types = attr.value
            .split("|")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        if (types.isEmpty()) return

        val declaredPermissions = collectDeclaredPermissions(element)

        for (type in types) {
            val requirements = TYPE_TO_REQUIREMENTS[type] ?: continue
            val missing = mutableListOf<String>()

            for (req in requirements) {
                val present = req.permissions.filter { it in declaredPermissions }
                if (req.requireAll) {
                    if (present.size != req.permissions.size) {
                        missing.addAll(req.permissions - present.toSet())
                    }
                } else if (present.isEmpty()) {
                    missing.addAll(req.permissions)
                }
            }

            if (missing.isNotEmpty()) {
                val message =
                    "Missing permission(s) required for foregroundServiceType '$type': ${missing.joinToString(", ")}"
                context.report(ISSUE, attr, context.getLocation(attr), message)
            }
        }
    }

    private fun collectDeclaredPermissions(element: Element): Set<String> {
        val document = element.ownerDocument ?: return emptySet()
        val permissions = mutableSetOf<String>()
        val tags = arrayOf(TAG_USES_PERMISSION, TAG_USES_PERMISSION_SDK_23)

        for (tag in tags) {
            val nodes = document.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                if (node is Element) {
                    val name = node.getAttributeNS(ANDROID_NS, ATTR_NAME)
                    if (name.isNotBlank()) {
                        permissions.add(name)
                    }
                }
            }
        }

        return permissions
    }
}