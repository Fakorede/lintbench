package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import java.util.EnumSet

class ForegroundServicePermissionDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE_PERMISSION = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For targetSdkVersion >= 34, each `foregroundServiceType` listed in the `<service>` element
                requires specific permissions to be declared in the manifest. If permissions are missing,
                a `SecurityException` will be thrown when the foreground service is started.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )

        private const val TAG_SERVICE = "service"
        private const val TAG_USES_SDK = "uses-sdk"
        private const val TAG_USES_PERMISSION = "uses-permission"

        private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"
        private const val ATTR_TARGET_SDK_VERSION = "targetSdkVersion"
        private const val ATTR_NAME = "name"

        private val REQUIRED_PERMISSIONS = mapOf(
            "camera" to listOf("android.permission.FOREGROUND_SERVICE_CAMERA"),
            "connectedDevice" to listOf("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"),
            "dataSync" to listOf("android.permission.FOREGROUND_SERVICE_DATA_SYNC"),
            "fileManagement" to listOf("android.permission.FOREGROUND_SERVICE_FILE_MANAGEMENT"),
            "health" to listOf("android.permission.FOREGROUND_SERVICE_HEALTH"),
            "location" to listOf("android.permission.FOREGROUND_SERVICE_LOCATION"),
            "mediaPlayback" to listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"),
            "mediaProcessing" to listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING"),
            "mediaProjection" to listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"),
            "microphone" to listOf("android.permission.FOREGROUND_SERVICE_MICROPHONE"),
            "phoneCall" to listOf("android.permission.FOREGROUND_SERVICE_PHONE_CALL"),
            "remoteMessaging" to listOf("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING"),
            "shortService" to listOf("android.permission.FOREGROUND_SERVICE_SHORT_SERVICE"),
            "specialUse" to listOf("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"),
            "systemExempted" to listOf("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"),
            "callCompanion" to listOf(
                "android.permission.FOREGROUND_SERVICE_CALL_COMPANION",
                "android.permission.MANAGE_OWN_CALLS"
            ),
            "automotiveProjection" to listOf("android.permission.FOREGROUND_SERVICE_AUTOMOTIVE_PROJECTION")
        )
    }

    override fun getApplicableFiles(): EnumSet<Scope> = Scope.MANIFEST_SCOPE

    override fun getApplicableElements(): Collection<String> = listOf(TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        if (getTargetSdk(context) < 34) return

        val foregroundServiceType =
            element.getAttributeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
        if (foregroundServiceType.isBlank()) return

        val declaredPermissions = getDeclaredPermissions(context)
        val types = foregroundServiceType.split(Regex("\\|"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        for (type in types) {
            val required = REQUIRED_PERMISSIONS[type] ?: continue
            val missing = required.filter { !declaredPermissions.contains(it) }
            if (missing.isNotEmpty()) {
                val missingList = missing.joinToString(", ")
                context.report(
                    ISSUE_PERMISSION,
                    element,
                    context.getLocation(element),
                    "Missing permissions for foregroundServiceType '$type': $missingList"
                )
            }
        }
    }

    private fun getTargetSdk(context: XmlContext): Int {
        val usesSdk = context.document.documentElement
            .getElementsByTagName(TAG_USES_SDK)
            .item(0) as? Element
        if (usesSdk != null) {
            val targetSdk = usesSdk.getAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION)
            if (targetSdk.isNotBlank()) {
                return targetSdk.toIntOrNull() ?: 0
            }
        }
        return context.project.targetSdk
    }

    private fun getDeclaredPermissions(context: XmlContext): Set<String> {
        val permissions = mutableSetOf<String>()
        val nodes = context.document.documentElement.getElementsByTagName(TAG_USES_PERMISSION)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            val name = node.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name.isNotBlank()) {
                permissions.add(name)
            }
        }
        return permissions
    }
}