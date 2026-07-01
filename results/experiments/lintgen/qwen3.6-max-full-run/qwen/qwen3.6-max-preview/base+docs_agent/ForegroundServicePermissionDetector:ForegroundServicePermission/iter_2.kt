package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class ForegroundServicePermissionDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = "For targetSdkVersion >= 34, each `foregroundServiceType` listed in the `<service>` element requires specific permissions to be declared in the manifest. If permissions are missing, a `SecurityException` will be thrown when the foreground service is started.",
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
            "remoteMessaging" to "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING",
            "specialUse" to "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            "systemExempted" to "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED",
            "mediaProcessing" to "android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING"
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(SdkConstants.TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.project.targetSdkVersion.apiLevel < 34) return

        val typeAttrNode = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_FOREGROUND_SERVICE_TYPE)
        if (typeAttrNode == null) return

        val typeAttrValue = typeAttrNode.value
        if (typeAttrValue.isEmpty() || typeAttrValue.startsWith("@")) return

        val types = typeAttrValue.split("|").map { it.trim() }
        val declaredPermissions = getDeclaredPermissions(context)

        for (type in types) {
            val requiredPermission = TYPE_TO_PERMISSION[type] ?: continue
            if (!declaredPermissions.contains(requiredPermission)) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(typeAttrNode),
                    "Missing permission `$requiredPermission` required for foreground service type `$type`"
                )
            }
        }
    }

    private fun getDeclaredPermissions(context: XmlContext): Set<String> {
        val permissions = mutableSetOf<String>()
        val root = context.document.documentElement ?: return permissions
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == SdkConstants.TAG_USES_PERMISSION) {
                val name = (node as Element).getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                if (name.isNotEmpty()) {
                    permissions.add(name)
                }
            }
        }
        return permissions
    }
}