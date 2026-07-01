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
import org.w3c.dom.Document
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : ResourceXmlDetector() {

    companion object {
        private const val TAG_SERVICE = "service"
        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val TAG_USES_PERMISSION_SDK_23 = "uses-permission-sdk-23"
        private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"
        private const val ATTR_NAME = "name"
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val TARGET_SDK_THRESHOLD = 34

        private val TYPE_PERMISSION_MAP = mapOf(
            "camera" to listOf("android.permission.FOREGROUND_SERVICE_CAMERA"),
            "connectedDevice" to listOf("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"),
            "dataSync" to listOf("android.permission.FOREGROUND_SERVICE_DATA_SYNC"),
            "health" to listOf("android.permission.FOREGROUND_SERVICE_HEALTH"),
            "location" to listOf("android.permission.FOREGROUND_SERVICE_LOCATION"),
            "mediaPlayback" to listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"),
            "mediaProjection" to listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"),
            "microphone" to listOf("android.permission.FOREGROUND_SERVICE_MICROPHONE"),
            "phoneCall" to listOf("android.permission.FOREGROUND_SERVICE_PHONE_CALL"),
            "remoteMessaging" to listOf("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING"),
            "shortService" to listOf("android.permission.FOREGROUND_SERVICE_SHORT_SERVICE"),
            "specialUse" to listOf("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"),
            "systemExempted" to listOf("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"),
        )

        private val IMPLEMENTATION = Implementation(
            ForegroundServicePermissionDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                Starting with Android 14 (API 34), apps that declare a foregroundServiceType
                on a <service> element must also declare the corresponding FOREGROUND_SERVICE_*
                permission in the manifest. If the required permission is missing, a
                SecurityException is thrown when the service is started.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.ownerDocument?.documentElement?.tagName != "manifest") {
            return
        }

        if (context.project.targetSdkVersion < TARGET_SDK_THRESHOLD) {
            return
        }

        val typeAttr = element.getAttributeNS(ANDROID_NS, ATTR_FOREGROUND_SERVICE_TYPE)
            .takeIf { it.isNotEmpty() }
            ?: return

        val declaredPermissions = getDeclaredPermissions(element.ownerDocument)
        val missingPermissions = mutableListOf<String>()

        for (type in typeAttr.split('|')) {
            val trimmed = type.trim()
            val requiredPermissions = TYPE_PERMISSION_MAP[trimmed] ?: continue
            for (permission in requiredPermissions) {
                if (permission !in declaredPermissions) {
                    missingPermissions.add(permission)
                }
            }
        }

        if (missingPermissions.isNotEmpty()) {
            val message = missingPermissions.joinToString(
                prefix = "Missing permissions required by foregroundServiceType: ",
                separator = ", ",
            )
            context.report(ISSUE, element, context.getLocation(element), message)
        }
    }

    private fun getDeclaredPermissions(document: Document?): Set<String> {
        val permissions = mutableSetOf<String>()
        if (document == null) return permissions

        val elements = document.getElementsByTagName("*")
        for (i in 0 until elements.length) {
            val node = elements.item(i) as? Element ?: continue
            if (node.tagName == TAG_USES_PERMISSION || node.tagName == TAG_USES_PERMISSION_SDK_23) {
                val name = node.getAttributeNS(ANDROID_NS, ATTR_NAME)
                if (name.isNotEmpty()) {
                    permissions.add(name)
                }
            }
        }
        return permissions
    }
}