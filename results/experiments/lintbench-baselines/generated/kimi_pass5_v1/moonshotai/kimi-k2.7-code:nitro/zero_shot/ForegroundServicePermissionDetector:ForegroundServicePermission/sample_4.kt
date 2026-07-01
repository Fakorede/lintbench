package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val TAG_SERVICE = "service"
private const val TAG_USES_PERMISSION = "uses-permission"
private const val TAG_USES_SDK = "uses-sdk"

private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"
private const val ATTR_TARGET_SDK_VERSION = "targetSdkVersion"

private const val ANDROID_PERMISSION_PREFIX = "android.permission."

private val FOREGROUND_SERVICE_PERMISSIONS =
    mapOf(
        "camera" to setOf("${ANDROID_PERMISSION_PREFIX}CAMERA"),
        "connectedDevice" to setOf(
            "${ANDROID_PERMISSION_PREFIX}BLUETOOTH_CONNECT",
            "${ANDROID_PERMISSION_PREFIX}BLUETOOTH_SCAN",
            "${ANDROID_PERMISSION_PREFIX}CHANGE_WIFI_MULTICAST_STATE",
            "${ANDROID_PERMISSION_PREFIX}CHANGE_WIFI_STATE",
            "${ANDROID_PERMISSION_PREFIX}NEARBY_WIFI_DEVICES",
            "${ANDROID_PERMISSION_PREFIX}UWB_RANGING"
        ),
        "dataSync" to setOf("${ANDROID_PERMISSION_PREFIX}FOREGROUND_SERVICE_DATA_SYNC"),
        "health" to setOf("${ANDROID_PERMISSION_PREFIX}HEALTH"),
        "location" to setOf(
            "${ANDROID_PERMISSION_PREFIX}ACCESS_COARSE_LOCATION",
            "${ANDROID_PERMISSION_PREFIX}ACCESS_FINE_LOCATION"
        ),
        "mediaPlayback" to setOf("${ANDROID_PERMISSION_PREFIX}FOREGROUND_SERVICE_MEDIA_PLAYBACK"),
        "mediaProjection" to setOf("${ANDROID_PERMISSION_PREFIX}FOREGROUND_SERVICE_MEDIA_PROJECTION"),
        "microphone" to setOf("${ANDROID_PERMISSION_PREFIX}RECORD_AUDIO"),
        "phoneCall" to setOf(
            "${ANDROID_PERMISSION_PREFIX}MANAGE_OWN_CALLS",
            "${ANDROID_PERMISSION_PREFIX}ANSWER_PHONE_CALLS",
            "${ANDROID_PERMISSION_PREFIX}READ_PHONE_STATE"
        ),
        "remoteMessaging" to setOf("${ANDROID_PERMISSION_PREFIX}FOREGROUND_SERVICE_REMOTE_MESSAGING"),
        "shortService" to setOf("${ANDROID_PERMISSION_PREFIX}FOREGROUND_SERVICE_SHORT_SERVICE"),
        "specialUse" to setOf("${ANDROID_PERMISSION_PREFIX}FOREGROUND_SERVICE_SPECIAL_USE")
    )

class ForegroundServicePermissionDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        if (!isTargetSdk34OrHigher(context)) {
            return
        }

        val typeValue = element.getAttributeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
        if (typeValue.isBlank()) {
            return
        }

        val declaredPermissions = context.document.declaredPermissions
        val missing = mutableListOf<Pair<String, Set<String>>>()

        for (rawType in typeValue.split('|')) {
            val type = rawType.trim().lowercase()
            if (type.isEmpty()) continue

            val required = FOREGROUND_SERVICE_PERMISSIONS[type] ?: continue
            if (required.none { it in declaredPermissions }) {
                missing.add(type to required)
            }
        }

        if (missing.isNotEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                buildMessage(missing)
            )
        }
    }

    private fun buildMessage(missing: List<Pair<String, Set<String>>>): String {
        val sb = StringBuilder(
            "Missing permissions required for the declared foregroundServiceType(s). " +
                    "On Android 14+ (targetSdkVersion 34+) a SecurityException will be thrown when starting this service:\n"
        )
        for ((type, permissions) in missing) {
            val sorted = permissions.sorted()
            sb.append(
                if (sorted.size == 1) {
                    "  - '$type' requires permission ${sorted[0]}\n"
                } else {
                    "  - '$type' requires at least one of: ${sorted.joinToString()}\n"
                }
            )
        }
        return sb.toString().trimEnd()
    }

    private fun isTargetSdk34OrHigher(context: XmlContext): Boolean {
        val projectTarget = context.project.targetSdk
        if (projectTarget > 0) {
            return projectTarget >= 34
        }
        return parseManifestTargetSdk(context)?.let { it >= 34 } ?: false
    }

    private fun parseManifestTargetSdk(context: XmlContext): Int? {
        val nodes = context.document.getElementsByTagName(TAG_USES_SDK)
        if (nodes.length == 0) {
            return null
        }
        val element = nodes.item(0) as? Element ?: return null
        val value = element.getAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION)
        return value.toIntOrNull()?.takeIf { it > 0 }
    }

    private val Document.declaredPermissions: Set<String>
        get() {
            val result = mutableSetOf<String>()
            val nodes = getElementsByTagName(TAG_USES_PERMISSION)
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as? Element ?: continue
                if (element.getAttributeNS(TOOLS_URI, "node") == "remove") continue
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME).takeIf { it.isNotBlank() } ?: continue
                result.add(name)
            }
            return result
        }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permission required by foregroundServiceType",
            explanation = """
                Starting with Android 14 (API 34), a <service> element must declare the
                permissions that correspond to each of its android:foregroundServiceType
                values. If the required permissions are missing, starting the service as
                a foreground service with that type will throw a SecurityException.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}