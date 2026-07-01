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
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ForegroundServicePermissionDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        private val FG_TYPE_PERMISSIONS = mapOf(
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
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = "For targetSdkVersion 34 and higher, each foregroundServiceType declared in a <service> element requires a corresponding FOREGROUND_SERVICE_<TYPE> permission to be declared in the manifest. Missing permissions will cause a SecurityException at runtime when the foreground service is started.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("service")

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.project.targetSdkVersion
        if (targetSdk != -1 && targetSdk < 34) return

        val fgTypeAttr = element.getAttribute("android:foregroundServiceType")
        if (fgTypeAttr.isEmpty()) return

        val declaredPermissions = getDeclaredPermissions(element)
        val attributeNode = element.getAttributeNode("android:foregroundServiceType")

        val types = fgTypeAttr.split("|")
        for (type in types) {
            val trimmedType = type.trim()
            val requiredPermission = FG_TYPE_PERMISSIONS[trimmedType]
            if (requiredPermission != null && !declaredPermissions.contains(requiredPermission)) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(attributeNode ?: element),
                    "Missing permission `$requiredPermission` required by foregroundServiceType `$trimmedType` for targetSdkVersion 34+"
                )
            }
        }
    }

    private fun getDeclaredPermissions(serviceElement: Element): Set<String> {
        val manifest = serviceElement.ownerDocument.documentElement
        val permissions = mutableSetOf<String>()
        val children = manifest.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == "uses-permission") {
                val name = node.getAttribute("android:name")
                if (name.isNotEmpty()) {
                    permissions.add(name)
                }
            }
        }
        return permissions
    }
}