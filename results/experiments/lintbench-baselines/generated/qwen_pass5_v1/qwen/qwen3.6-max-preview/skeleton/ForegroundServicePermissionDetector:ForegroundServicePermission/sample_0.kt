package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import java.io.File
import java.util.EnumSet
import javax.xml.parsers.DocumentBuilderFactory
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
            explanation = "For targetSdkVersion >= 34, each foregroundServiceType listed in the <service> element requires specific sets of permissions to be declared in the manifest. If permissions are missing, a SecurityException will be thrown when the foreground service is started.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private val FGS_TYPE_PERMISSIONS = mapOf(
            "camera" to listOf("android.permission.CAMERA"),
            "connectedDevice" to listOf("android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_ADMIN", "android.permission.NFC"),
            "health" to listOf("android.permission.BODY_SENSORS", "android.permission.BODY_SENSORS_BACKGROUND"),
            "location" to listOf("android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"),
            "microphone" to listOf("android.permission.RECORD_AUDIO"),
            "phoneCall" to listOf("android.permission.CALL_PHONE", "android.permission.READ_PHONE_STATE", "android.permission.READ_PHONE_NUMBERS", "android.permission.ANSWER_PHONE_CALLS")
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("service")

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.file.name != "AndroidManifest.xml") return

        val targetSdk = context.mainProject.targetSdkVersion ?: context.project.targetSdkVersion ?: return
        if (targetSdk < 34) return

        val attrNode = element.getAttributeNode("android:foregroundServiceType") ?: return
        val fgsTypes = attrNode.value.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        if (fgsTypes.isEmpty()) return

        val declaredPermissions = getDeclaredPermissions(context)

        for (type in fgsTypes) {
            val requiredPermissions = FGS_TYPE_PERMISSIONS[type] ?: continue
            val hasRequired = requiredPermissions.any { declaredPermissions.contains(it) }
            if (!hasRequired) {
                val missing = requiredPermissions.joinToString(" or ")
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(attrNode),
                    "Missing permissions required by foregroundServiceType '$type': must declare $missing"
                )
            }
        }
    }

    private fun getDeclaredPermissions(context: XmlContext): Set<String> {
        val manifestFile: File? = context.mainProject.getManifest() ?: context.project.getManifest()
        if (manifestFile == null || !manifestFile.exists()) return emptySet()

        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(manifestFile)
            val permissions = mutableSetOf<String>()
            val nodes = doc.getElementsByTagName("uses-permission")
            for (i in 0 until nodes.length) {
                val node = nodes.item(i) as? Element ?: continue
                val name = node.getAttribute("android:name")
                if (name.isNotEmpty()) {
                    permissions.add(name)
                }
            }
            permissions
        } catch (e: Exception) {
            emptySet()
        }
    }
}