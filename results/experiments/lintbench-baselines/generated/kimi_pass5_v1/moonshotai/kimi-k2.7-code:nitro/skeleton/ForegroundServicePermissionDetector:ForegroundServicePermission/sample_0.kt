package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import java.util.EnumSet
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
private const val TAG_SERVICE = "service"
private const val TAG_USES_PERMISSION = "uses-permission"
private const val TAG_USES_SDK = "uses-sdk"
private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"
private const val ATTR_NAME = "name"
private const val ATTR_TARGET_SDK_VERSION = "targetSdkVersion"

private val FOREGROUND_SERVICE_TYPE_PERMISSIONS: Map<String, List<String>> = mapOf(
    "camera" to listOf("android.permission.FOREGROUND_SERVICE_CAMERA"),
    "connectedDevice" to listOf("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"),
    "dataSync" to listOf("android.permission.FOREGROUND_SERVICE_DATA_SYNC"),
    "health" to listOf("android.permission.FOREGROUND_SERVICE_HEALTH"),
    "location" to listOf("android.permission.FOREGROUND_SERVICE_LOCATION"),
    "mediaPlayback" to listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"),
    "mediaProjection" to listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"),
    "microphone" to listOf("android.permission.FOREGROUND_SERVICE_MICROPHONE"),
    "phoneCall" to listOf("android.permission.FOREGROUND_SERVICE_PHONE_CALL"),
    "shortService" to listOf("android.permission.FOREGROUND_SERVICE_SHORT_SERVICE"),
    "specialUse" to listOf("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"),
    "systemExempted" to listOf("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"),
    "fileProcessing" to listOf("android.permission.FOREGROUND_SERVICE_FILE_PROCESSING"),
    "remoteMessaging" to listOf("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING"),
)

class ForegroundServicePermissionDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ForegroundServicePermissionDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = "For apps targeting Android 14 (API 34) and higher, every " +
                "foreground service type declared via `android:foregroundServiceType` on a " +
                "`<service>` element requires a corresponding " +
                "`android.permission.FOREGROUND_SERVICE_*` permission in the manifest. If the " +
                "required permission is missing, `startForeground()` throws a " +
                "`SecurityException`.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        if (!isTargetSdk34OrHigher(context)) {
            return
        }

        if (!element.hasAttributeNS(ANDROID_NS, ATTR_FOREGROUND_SERVICE_TYPE)) {
            return
        }

        val typeValue = element.getAttributeNS(ANDROID_NS, ATTR_FOREGROUND_SERVICE_TYPE)
        if (typeValue.isBlank() || typeValue.startsWith("@")) {
            return
        }

        val declaredPermissions = getDeclaredPermissions(context)
        val typeLocation = getForegroundServiceTypeLocation(context, element)

        for (type in typeValue.split("|")) {
            val trimmed = type.trim()
            val requiredPermissions = FOREGROUND_SERVICE_TYPE_PERMISSIONS[trimmed] ?: continue

            val missing = requiredPermissions.filter { it !in declaredPermissions }
            if (missing.isEmpty()) {
                continue
            }

            val message = buildString {
                append("The foreground service type '")
                append(trimmed)
                append("' requires the permission")
                if (missing.size > 1) {
                    append("s")
                }
                append(" ")
                append(missing.joinToString(", "))
                append(" to be declared in the manifest.")
            }
            context.report(ISSUE, typeLocation, message)
        }
    }

    private fun isTargetSdk34OrHigher(context: XmlContext): Boolean {
        val projectTargetSdk = context.project.targetSdk
        if (projectTargetSdk > 0) {
            return projectTargetSdk >= 34
        }

        val manifestRoot = context.document.documentElement ?: return false
        val usesSdkList = manifestRoot.getElementsByTagName(TAG_USES_SDK)
        for (i in 0 until usesSdkList.length) {
            val usesSdk = usesSdkList.item(i) as? Element ?: continue
            val targetSdk = usesSdk.getAttributeNS(ANDROID_NS, ATTR_TARGET_SDK_VERSION)
                .takeIf { it.isNotBlank() }
                ?: continue
            return (targetSdk.toIntOrNull() ?: 0) >= 34
        }
        return false
    }

    private fun getDeclaredPermissions(context: XmlContext): Set<String> {
        val manifestRoot = context.document.documentElement ?: return emptySet()
        val permissions = mutableSetOf<String>()
        collectUsesPermissions(manifestRoot, permissions)
        return permissions
    }

    private fun collectUsesPermissions(node: Node, out: MutableSet<String>) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element
            if (element.localName == TAG_USES_PERMISSION ||
                element.nodeName == TAG_USES_PERMISSION ||
                element.tagName == TAG_USES_PERMISSION
            ) {
                val name = element.getAttributeNS(ANDROID_NS, ATTR_NAME)
                    .ifBlank { element.getAttribute(ATTR_NAME) }
                if (name.isNotBlank()) {
                    out.add(name)
                }
            }
        }

        val children = node.childNodes ?: return
        for (i in 0 until children.length) {
            collectUsesPermissions(children.item(i), out)
        }
    }

    private fun getForegroundServiceTypeLocation(context: XmlContext, element: Element): Location {
        val attr = element.getAttributeNodeNS(ANDROID_NS, ATTR_FOREGROUND_SERVICE_TYPE)
        return if (attr != null) context.getLocation(attr) else context.getLocation(element)
    }
}