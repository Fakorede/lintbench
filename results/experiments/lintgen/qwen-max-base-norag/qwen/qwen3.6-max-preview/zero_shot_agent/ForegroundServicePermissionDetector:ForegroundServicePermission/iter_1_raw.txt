package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Document
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : Detector(), XmlScanner {

    companion object {
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
            "specialUse" to "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            "systemExempted" to "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"
        )

        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = "For targetSdkVersion >= 34, each `foregroundServiceType` listed in the `<service>` element requires specific sets of permissions to be declared in the manifest. If permissions are missing, then when the foreground service is started with a `foregroundServiceType` that has missing permissions, a `SecurityException` will be thrown.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(SdkConstants.TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.project.targetSdkVersion?.apiLevel ?: return
        if (targetSdk < 34) return

        val typeAttr = element.getAttributeNS(SdkConstants.ANDROID_URI, "foregroundServiceType")
        if (typeAttr.isEmpty()) return

        val types = typeAttr.split("|").map { it.trim() }
        val manifest = element.ownerDocument ?: return
        val declaredPermissions = getDeclaredPermissions(manifest)

        for (type in types) {
            val requiredPermission = TYPE_TO_PERMISSION[type] ?: continue
            if (requiredPermission !in declaredPermissions) {
                val message = "Missing permission $requiredPermission required for foregroundServiceType \"$type\" when targeting SDK 34+"
                context.report(ISSUE, element, message)
            }
        }
    }

    private fun getDeclaredPermissions(manifest: Document): Set<String> {
        val permissions = mutableSetOf<String>()
        val nodes = manifest.getElementsByTagName("uses-permission")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) {
                val name = node.getAttributeNS(SdkConstants.ANDROID_URI, "name")
                if (name.isNotEmpty()) {
                    permissions.add(name)
                }
            }
        }
        return permissions
    }
}