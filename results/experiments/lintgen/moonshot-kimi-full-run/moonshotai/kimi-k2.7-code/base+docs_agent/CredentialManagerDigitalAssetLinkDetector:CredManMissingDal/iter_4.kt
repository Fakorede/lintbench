package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements string
                resource file that includes the assetlinks.json files to load must be declared in the
                manifest using a `<meta-data android:name="asset_statements" android:resource="@string/..." />`
                element.

                See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST, Scope.RESOURCE_FILE)
            )
        )

        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val ASSET_STATEMENTS = "asset_statements"

        private const val GET_PASSWORD_OPTION = "androidx.credentials.GetPasswordOption"
        private const val CREATE_PASSWORD_REQUEST = "androidx.credentials.CreatePasswordRequest"
    }

    private var credentialManagerUsage: Location? = null
    private val metaDataInfos = mutableListOf<MetaDataInfo>()
    private val stringResources = mutableMapOf<String, Pair<String, Location>>()

    private data class MetaDataInfo(
        val location: Location,
        val resourceAttributeLocation: Location?,
        val resourceName: String?
    )

    override fun beforeCheckRootProject(context: Context) {
        credentialManagerUsage = null
        metaDataInfos.clear()
        stringResources.clear()
    }

    override fun getApplicableConstructorTypes(): List<String>? {
        return listOf(GET_PASSWORD_OPTION, CREATE_PASSWORD_REQUEST)
    }

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        credentialManagerUsage = context.getLocation(node)
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("meta-data", "string")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.localName) {
            "meta-data" -> visitMetaData(context, element)
            "string" -> visitStringResource(context, element)
        }
    }

    private fun visitMetaData(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_NS, "name")
        if (name != ASSET_STATEMENTS) {
            return
        }

        val resource = element.getAttributeNS(ANDROID_NS, "resource")
        val resourceName: String?
        val resourceAttributeLocation: Location?
        if (resource.isNotBlank() && resource.startsWith("@string/")) {
            val candidate = resource.substringAfter("@string/")
            if (candidate.isNotBlank()) {
                resourceName = candidate
                resourceAttributeLocation =
                    element.getAttributeNodeNS(ANDROID_NS, "resource")?.let { context.getLocation(it) }
            } else {
                resourceName = null
                resourceAttributeLocation = null
            }
        } else {
            resourceName = null
            resourceAttributeLocation = null
        }

        metaDataInfos.add(
            MetaDataInfo(
                location = context.getLocation(element),
                resourceAttributeLocation = resourceAttributeLocation,
                resourceName = resourceName
            )
        )
    }

    private fun visitStringResource(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_NS, "name")
        if (name.isBlank()) {
            return
        }
        val value = element.textContent?.trim() ?: ""
        stringResources[name] = value to context.getLocation(element)
    }

    override fun afterCheckRootProject(context: Context) {
        val usage = credentialManagerUsage ?: return
        if (hasValidDal()) {
            return
        }

        context.report(
            ISSUE,
            usage,
            "Missing Digital Asset Link declaration: add a `<meta-data android:name=\"asset_statements\" android:resource=\"@string/...\" />` element to AndroidManifest.xml"
        )
    }

    private fun hasValidDal(): Boolean {
        if (metaDataInfos.isEmpty()) {
            return false
        }

        return metaDataInfos.any { info ->
            val resourceName = info.resourceName ?: return@any false
            val value = stringResources[resourceName]?.first ?: return@any false
            hasValidIncludeUrl(value)
        }
    }

    private fun hasValidIncludeUrl(value: String): Boolean {
        val normalized = value.replace("\\\"", "\"").replace("\\/", "/")
        val regex = "\"include\"\\s*:\\s*\"(https?://[^\"]+)\"".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(normalized) ?: return false
        return match.groupValues[1].isNotBlank()
    }
}