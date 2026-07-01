package com.android.tools.lint.checks

import com.android.SdkConstants
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

    private val usages = mutableListOf<Usage>()
    private val manifestMetaData = mutableListOf<MetaDataDecl>()
    private val stringResources = mutableListOf<StringRes>()

    private class Usage(val location: Location, val context: JavaContext)
    private class MetaDataDecl(
        val resourceName: String?,
        val location: Location,
        val context: Context
    )
    private class StringRes(val name: String, val value: String)

    override fun beforeCheckRootProject(context: Context) {
        usages.clear()
        manifestMetaData.clear()
        stringResources.clear()
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            "androidx.credentials.GetPasswordOption",
            "androidx.credentials.CreatePasswordRequest"
        )
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        candidate: PsiMethod
    ) {
        usages.add(Usage(context.getLocation(node), context))
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("meta-data", "string")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName == "meta-data") {
            val name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name")
            if (name == "asset_statements") {
                val resource = element.getAttributeNS(SdkConstants.ANDROID_URI, "resource")
                val resourceName = if (resource.isNotEmpty()) {
                    resource.substringAfter("@string/")
                } else {
                    null
                }
                manifestMetaData.add(MetaDataDecl(resourceName, context.getLocation(element), context))
            }
        } else if (element.tagName == "string") {
            val name = element.getAttribute("name")
            if (name.isNotEmpty()) {
                val text = element.textContent
                stringResources.add(StringRes(name, text))
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        if (usages.isEmpty()) return

        if (manifestMetaData.isEmpty()) {
            for (usage in usages) {
                usage.context.report(
                    ISSUE,
                    usage.location,
                    "Missing Digital Asset Link declaration in AndroidManifest.xml"
                )
            }
            return
        }

        var hasValidMetaData = false
        var missingResourceAttribute = false
        var missingStringResource = false
        var missingInclude = false
        var missingUrl = false

        for (meta in manifestMetaData) {
            val resName = meta.resourceName
            if (resName == null) {
                missingResourceAttribute = true
                continue
            }

            val matchingStrings = stringResources.filter { it.name == resName }
            if (matchingStrings.isEmpty()) {
                missingStringResource = true
                continue
            }

            for (str in matchingStrings) {
                val jsonText = str.value
                val includeUrl = extractIncludeUrl(jsonText)
                if (includeUrl == null) {
                    missingInclude = true
                } else if (!isValidUrl(includeUrl)) {
                    missingUrl = true
                } else {
                    hasValidMetaData = true
                    break
                }
            }
            if (hasValidMetaData) break
        }

        if (!hasValidMetaData) {
            val message = when {
                missingResourceAttribute -> "Missing `android:resource` attribute in `<meta-data>` tag"
                missingStringResource -> "Missing string resource for Digital Asset Link"
                missingUrl -> "Missing or invalid URL in Digital Asset Link"
                missingInclude -> "Missing `include` statement in Digital Asset Link"
                else -> "Missing Digital Asset Link declaration in AndroidManifest.xml"
            }

            for (usage in usages) {
                usage.context.report(
                    ISSUE,
                    usage.location,
                    message
                )
            }
        }
    }

    private fun extractIncludeUrl(json: String): String? {
        val regex = """['"\\\s]*include['"\\\s]*:\s*['"\\\s]*([^'"\\\s]+)""".toRegex()
        val match = regex.find(json)
        return match?.groupValues?.get(1)
    }

    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("https://") && url.length > 8
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, you must associate your \
                app with your website by declaring an asset statements string resource file \
                that includes the `assetlinks.json` files to load. This is done by adding a \
                `<meta-data>` element with `android:name="asset_statements"` in your `AndroidManifest.xml`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST, Scope.RESOURCE_FILE)
            )
        )
    }
}