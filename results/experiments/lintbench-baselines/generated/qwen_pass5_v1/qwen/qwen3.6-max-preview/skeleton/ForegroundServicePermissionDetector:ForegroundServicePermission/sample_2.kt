package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
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

        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = "For targetSdkVersion >= 34, each foregroundServiceType listed in the <service> element requires specific permissions to be declared in the manifest. If permissions are missing, a SecurityException will be thrown when the foreground service is started.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        private val FGS_TYPE_PERMISSIONS = mapOf(
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
            "specialUse" to listOf("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"),
            "systemExempted" to listOf("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED")
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("service")

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.targetSdk < 34) return

        val fgsTypeAttr = element.getAttributeNodeNS(ANDROID_NS, "foregroundServiceType") ?: return
        val rawValue = fgsTypeAttr.value
        if (rawValue.isEmpty() || rawValue.startsWith("@")) return

        val types = rawValue.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        if (types.isEmpty()) return

        val declaredPermissions = getDeclaredPermissions(context)
        val missingPermissions = mutableSetOf<String>()

        for (type in types) {
            val required = FGS_TYPE_PERMISSIONS[type] ?: continue
            for (perm in required) {
                if (perm !in declaredPermissions) {
                    missingPermissions.add(perm)
                }
            }
        }

        if (missingPermissions.isNotEmpty()) {
            val message = "Foreground service type(s) ${types.joinToString()} require the following permissions to be declared in the manifest: ${missingPermissions.joinToString()}"
            context.report(ISSUE, fgsTypeAttr, context.getLocation(fgsTypeAttr), message)
        }
    }

    private fun getDeclaredPermissions(context: XmlContext): Set<String> {
        val manifest = context.document.documentElement ?: return emptySet()
        val permissions = mutableSetOf<String>()
        collectPermissions(manifest, "uses-permission", permissions)
        collectPermissions(manifest, "uses-permission-sdk-23", permissions)
        return permissions
    }

    private fun collectPermissions(manifest: Element, tagName: String, out: MutableSet<String>) {
        val nodes = manifest.getElementsByTagName(tagName)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) {
                val name = node.getAttributeNS(ANDROID_NS, "name")
                if (name.isNotEmpty()) {
                    out.add(name)
                }
            }
        }
    }
}