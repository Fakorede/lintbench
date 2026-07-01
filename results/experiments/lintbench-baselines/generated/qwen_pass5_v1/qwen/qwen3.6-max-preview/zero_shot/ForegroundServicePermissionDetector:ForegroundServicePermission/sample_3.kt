package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For targetSdkVersion >= 34, each `foregroundServiceType` listed in the `<service>` \
                element requires specific permissions to be declared in the manifest. If permissions \
                are missing, a `SecurityException` will be thrown when the foreground service is started.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST
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
            "remoteMessaging" to "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING",
            "specialUse" to "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.getMainProject().getTargetSdk()?.apiLevel ?: return
        if (targetSdk < 34) return

        val typeAttr = element.getAttributeNS(ANDROID_URI, "foregroundServiceType")
        if (typeAttr.isEmpty() || typeAttr.startsWith("@")) return

        val declaredPermissions = getDeclaredPermissions(context)
        val types = typeAttr.split("|")

        for (type in types) {
            val trimmedType = type.trim()
            val requiredPermission = TYPE_TO_PERMISSION[trimmedType] ?: continue

            if (!declaredPermissions.contains(requiredPermission)) {
                val location = context.getLocation(
                    element.getAttributeNodeNS(ANDROID_URI, "foregroundServiceType")
                )
                context.report(
                    ISSUE,
                    element,
                    location,
                    "Missing permission `$requiredPermission` required by foregroundServiceType `$trimmedType`"
                )
            }
        }
    }

    private fun getDeclaredPermissions(context: XmlContext): Set<String> {
        val permissions = mutableSetOf<String>()
        val doc = context.document ?: return permissions
        val nodes = doc.getElementsByTagName(TAG_USES_PERMISSION)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) {
                val name = node.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name.isNotEmpty()) {
                    permissions.add(name)
                }
            }
        }
        return permissions
    }
}