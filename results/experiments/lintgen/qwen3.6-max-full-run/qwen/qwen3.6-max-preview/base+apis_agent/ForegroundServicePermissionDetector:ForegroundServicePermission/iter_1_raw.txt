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
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : Detector(), XmlScanner {
    companion object {
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = "For targetSdkVersion >= 34, each foregroundServiceType listed in the <service> element requires specific sets of permissions to be declared in the manifest. If permissions are missing, a SecurityException will be thrown when the service is started.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )

        private val TYPE_TO_PERMISSION = mapOf(
            "camera" to "android.permission.FOREGROUND_SERVICE_CAMERA",
            "connectedDevice" to "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
            "dataSync" to "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            "health" to "android.permission.FOREGROUND_SERVICE_HEALTH",
            "location" to "android.permission.FOREGROUND_SERVICE_LOCATION",
            "mediaPlayback" to "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            "mediaProjection" to "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
            "microphone" to "android.permission.FOREGROUND_SERVICE_MICROPHONE",
            "phoneCall" to "android.permission.FOREGROUND_SERVICE_PHONE_CALL",
            "remoteMessaging" to "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING"
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(SdkConstants.TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.mainProject.targetSdkVersion?.apiLevel ?: return
        if (targetSdk < 34) return

        val fgTypeAttr = element.getAttributeNS(SdkConstants.ANDROID_URI, "foregroundServiceType")
        if (fgTypeAttr.isEmpty()) return

        val types = fgTypeAttr.split("|").map { it.trim() }
        val requiredPermissions = types.mapNotNull { TYPE_TO_PERMISSION[it] }.toSet()
        if (requiredPermissions.isEmpty()) return

        val declaredPermissions = getDeclaredPermissions(context)
        val missingPermissions = requiredPermissions - declaredPermissions

        if (missingPermissions.isNotEmpty()) {
            val message = "Missing permission(s) required for foregroundServiceType: ${missingPermissions.joinToString(", ")}"
            context.report(ISSUE, element, context.getLocation(element), message)
        }
    }

    private fun getDeclaredPermissions(context: XmlContext): Set<String> {
        val doc = context.document ?: return emptySet()
        val nodes = doc.getElementsByTagName(SdkConstants.TAG_USES_PERMISSION)
        val permissions = mutableSetOf<String>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) {
                val name = node.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                if (name.isNotEmpty()) {
                    permissions.add(name)
                }
            }
        }
        return permissions
    }
}