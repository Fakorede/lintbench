package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : Detector(), Detector.XmlScanner {

    companion object {
        private val TYPE_TO_PERMISSION_MAP = mapOf(
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

        @JvmField
        val ISSUE: Issue = Issue.create(
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
        if (context.targetSdkVersion < 34) return

        val attr = element.getAttributeNode(SdkConstants.ANDROID_URI, "foregroundServiceType")
        if (attr == null || attr.value.isBlank() || attr.value.startsWith("@")) return

        val types = attr.value.split("|").map { it.trim() }
        val declaredPermissions = getDeclaredPermissions(context)

        for (type in types) {
            val requiredPermission = TYPE_TO_PERMISSION_MAP[type] ?: continue
            if (requiredPermission !in declaredPermissions) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(attr),
                    "Missing permission `$requiredPermission` required by `foregroundServiceType=\"$type\"`"
                )
            }
        }
    }

    private fun getDeclaredPermissions(context: XmlContext): Set<String> {
        val permissions = mutableSetOf<String>()
        val manifest = context.document.documentElement ?: return permissions
        val usesPermissions = manifest.getElementsByTagName(SdkConstants.TAG_USES_PERMISSION)
        for (i in 0 until usesPermissions.length) {
            val permElement = usesPermissions.item(i) as? Element ?: continue
            val name = permElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name.isNotEmpty()) {
                permissions.add(name)
            }
        }
        return permissions
    }
}