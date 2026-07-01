package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = listOf(SdkConstants.TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.project.targetSdkVersion ?: return
        if (targetSdk < 34) return

        val attrNode = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
        if (attrNode == null || attrNode.value.isBlank()) return

        val fgTypes = attrNode.value.split("|").map { it.trim() }
        val declaredPermissions = getDeclaredPermissions(element)
        val missingPermissions = mutableSetOf<String>()

        for (type in fgTypes) {
            val required = FG_TYPE_PERMISSIONS[type] ?: continue
            for (perm in required) {
                if (!declaredPermissions.contains(perm)) {
                    missingPermissions.add(perm)
                }
            }
        }

        if (missingPermissions.isNotEmpty()) {
            val message = "Missing permissions required by foregroundServiceType '${attrNode.value}': " +
                    missingPermissions.joinToString(", ")
            context.report(ISSUE, context.getLocation(attrNode), message)
        }
    }

    private fun getDeclaredPermissions(element: Element): Set<String> {
        val doc = element.ownerDocument ?: return emptySet()
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

    companion object {
        private const val ATTR_FOREGROUND_SERVICE_TYPE = "foregroundServiceType"

        private val FG_TYPE_PERMISSIONS = mapOf(
            "camera" to listOf("android.permission.CAMERA", "android.permission.FOREGROUND_SERVICE_CAMERA"),
            "connectedDevice" to listOf("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"),
            "dataSync" to listOf("android.permission.FOREGROUND_SERVICE_DATA_SYNC"),
            "health" to listOf("android.permission.FOREGROUND_SERVICE_HEALTH"),
            "location" to listOf("android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION", "android.permission.FOREGROUND_SERVICE_LOCATION"),
            "mediaPlayback" to listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"),
            "mediaProjection" to listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"),
            "microphone" to listOf("android.permission.RECORD_AUDIO", "android.permission.FOREGROUND_SERVICE_MICROPHONE"),
            "phoneCall" to listOf("android.permission.FOREGROUND_SERVICE_PHONE_CALL"),
            "remoteMessaging" to listOf("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING"),
            "systemExempted" to listOf("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED")
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
}