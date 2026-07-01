package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : Detector(), Detector.XmlScanner {

    companion object {
        private val FGS_TYPE_TO_PERMISSION = mapOf(
            "camera" to "android.permission.FOREGROUND_SERVICE_CAMERA",
            "connectedDevice" to "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
            "dataSync" to "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            "health" to "android.permission.FOREGROUND_SERVICE_HEALTH",
            "location" to "android.permission.FOREGROUND_SERVICE_LOCATION",
            "mediaPlayback" to "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            "mediaProjection" to "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
            "microphone" to "android.permission.FOREGROUND_SERVICE_MICROPHONE",
            "phoneCall" to "android.permission.FOREGROUND_SERVICE_PHONE_CALL",
            "remoteMessaging" to "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING",
            "specialUse" to "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            "systemExempted" to "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED",
            "fileManagement" to "android.permission.FOREGROUND_SERVICE_FILE_MANAGEMENT"
        )

        val ISSUE_PERMISSION = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For targetSdkVersion >= 34, each `foregroundServiceType` listed in the `<service>` element \
                requires specific sets of permissions to be declared in the manifest. If permissions are \
                missing, then when the foreground service is started with a `foregroundServiceType` that has \
                missing permissions, a `SecurityException` will be thrown.
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

    override fun getApplicableElements(): Collection<String>? {
        return listOf(SdkConstants.TAG_SERVICE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.project.targetSdkVersion
        if (targetSdk < 34) return

        val typeAttrNode = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "foregroundServiceType") ?: return
        val typeAttrValue = typeAttrNode.value
        if (typeAttrValue.isBlank() || typeAttrValue.startsWith("@")) return

        val types = typeAttrValue.split("|").map { it.trim() }
        val requiredPermissions = types.mapNotNull { FGS_TYPE_TO_PERMISSION[it] }.distinct()
        if (requiredPermissions.isEmpty()) return

        val declaredPermissions = getDeclaredPermissions(context)
        val missingPermissions = requiredPermissions.filter { it !in declaredPermissions }

        if (missingPermissions.isNotEmpty()) {
            val message = "Missing required permission(s) for foregroundServiceType: ${missingPermissions.joinToString(", ")}"
            context.report(ISSUE_PERMISSION, element, context.getLocation(typeAttrNode), message)
        }
    }

    private fun getDeclaredPermissions(context: XmlContext): Set<String> {
        val manifest = context.document.documentElement ?: return emptySet()
        val permissions = mutableSetOf<String>()
        val childNodes = manifest.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is Element && node.tagName == SdkConstants.TAG_USES_PERMISSION) {
                val name = node.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                if (name.isNotEmpty()) {
                    permissions.add(name)
                }
            }
        }
        return permissions
    }
}