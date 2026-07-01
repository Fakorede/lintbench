package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_TARGET_SDK_VERSION
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.SdkConstants.TAG_USES_SDK
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import java.util.Locale

class ForegroundServicePermissionDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String> = listOf(TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        val root = context.document.documentElement ?: return
        val targetSdk = getTargetSdk(context, root)
        if (targetSdk < MIN_TARGET_SDK) {
            return
        }

        val attrNode = element.getAttributeNodeNS(ANDROID_URI, FGS_TYPE_ATTR) ?: return
        val attrValue = attrNode.value ?: return
        if (attrValue.isBlank()) {
            return
        }

        val declaredPermissions = collectPermissions(root)
        val location = context.getLocation(attrNode)

        val types = attrValue.split(Regex("\\s*\\|\\s*"))
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }

        for (type in types) {
            val requirements = REQUIRED_PERMISSIONS[type] ?: continue
            val missingGroups = requirements.filter { group ->
                group.none { it in declaredPermissions }
            }
            if (missingGroups.isNotEmpty()) {
                val missingText = missingGroups.joinToString("; ") { group ->
                    if (group.size == 1) {
                        "\"${group[0]}\""
                    } else {
                        "one of ${group.joinToString { "\"$it\"" }}"
                    }
                }
                val message =
                    "The `foregroundServiceType` \"$type\" requires permissions that have not been declared: $missingText"
                context.report(ISSUE, location, message)
            }
        }
    }

    private fun getTargetSdk(context: XmlContext, root: Element): Int {
        val usesSdk = root.getElementsByTagName(TAG_USES_SDK).item(0) as? Element
        val manifestTarget = usesSdk
            ?.getAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION)
            ?.trim()
            ?.toIntOrNull()
        return manifestTarget ?: context.project.targetSdkVersion.featureLevel
    }

    private fun collectPermissions(root: Element): Set<String> {
        val result = mutableSetOf<String>()
        val nodes = root.getElementsByTagName(TAG_USES_PERMISSION)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            val name = node.getAttributeNS(ANDROID_URI, ATTR_NAME).trim()
            if (name.isNotEmpty()) {
                result.add(name)
            }
        }
        return result
    }

    companion object {
        private const val MIN_TARGET_SDK = 34
        private const val FGS_TYPE_ATTR = "foregroundServiceType"

        private val REQUIRED_PERMISSIONS: Map<String, List<List<String>>> = mapOf(
            "dataSync" to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_DATA_SYNC")
            ),
            "health" to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_HEALTH"),
                listOf("android.permission.HEALTH")
            ),
            "remoteMessaging" to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING")
            ),
            "systemExposed" to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_SYSTEM_EXPOSED")
            ),
            "camera" to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_CAMERA"),
                listOf("android.permission.CAMERA")
            ),
            "connectedDevice" to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"),
                listOf(
                    "android.permission.BLUETOOTH_CONNECT",
                    "android.permission.BLUETOOTH_SCAN",
                    "android.permission.BLUETOOTH_ADVERTISE",
                    "android.permission.UWB_RANGING"
                )
            ),
            "mediaPlayback" to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK")
            ),
            "mediaProjection" to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION")
            ),
            "microphone" to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_MICROPHONE"),
                listOf("android.permission.RECORD_AUDIO")
            ),
            "phoneCall" to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_PHONE_CALL"),
                listOf(
                    "android.permission.CALL_PHONE",
                    "android.permission.MANAGE_OWN_CALLS"
                )
            ),
            "location" to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_LOCATION"),
                listOf(
                    "android.permission.ACCESS_COARSE_LOCATION",
                    "android.permission.ACCESS_FINE_LOCATION"
                )
            ),
            "specialUse" to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_SPECIAL_USE")
            ),
            "shortService" to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_SHORT_SERVICE")
            ),
            "fileManagement" to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_FILE_MANAGEMENT")
            ),
            "mediaProcessing" to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING")
            ),
            "calendar" to listOf(
                listOf("android.permission.FOREGROUND_SERVICE_CALENDAR"),
                listOf(
                    "android.permission.READ_CALENDAR",
                    "android.permission.WRITE_CALENDAR"
                )
            )
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For targetSdkVersion 34 and higher, each foregroundServiceType declared in a
                <service> element requires one or more corresponding permissions to be declared in
                the manifest. If any required permission is missing, the app will throw a
                SecurityException when startForeground() is called with that type.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}