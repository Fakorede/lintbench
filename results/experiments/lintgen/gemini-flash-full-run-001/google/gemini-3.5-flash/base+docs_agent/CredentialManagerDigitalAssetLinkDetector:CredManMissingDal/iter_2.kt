package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.client.api.UElementHandler
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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastCallKind
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

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.kind == UastCallKind.CONSTRUCTOR_CALL) {
                    val type = node.returnType
                    val fqName = type?.canonicalText
                    val isTarget = if (fqName != null) {
                        fqName == "androidx.credentials.GetPasswordOption" ||
                        fqName == "androidx.credentials.CreatePasswordRequest" ||
                        fqName == "GetPasswordOption" ||
                        fqName == "CreatePasswordRequest"
                    } else {
                        val refName = node.classReference?.asSourceString()
                        refName == "GetPasswordOption" ||
                        refName == "CreatePasswordRequest" ||
                        refName?.endsWith(".GetPasswordOption") == true ||
                        refName?.endsWith(".CreatePasswordRequest") == true
                    }
                    if (isTarget) {
                        usages.add(Usage(context.getLocation(node), context))
                    }
                }
            }
        }
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
            usages.clear()
            manifestMetaData.clear()
            stringResources.clear()
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

        usages.clear()
        manifestMetaData.clear()
        stringResources.clear()
    }

    private fun extractIncludeUrl(json: String): String? {
        val includeIndex = json.indexOf("\"include\"")
        val indexToUse = if (includeIndex != -1) {
            includeIndex
        } else {
            val includeIndexSingle = json.indexOf("'include'")
            if (includeIndexSingle != -1) {
                includeIndexSingle
            } else {
                return null
            }
        }
        
        val colonIndex = json.indexOf(":", indexToUse)
        if (colonIndex == -1) return ""
        
        val startQuote = json.indexOfAny(charArrayOf('"', '\''), colonIndex + 1)
        if (startQuote == -1) return ""
        
        val endQuote = json.indexOfAny(charArrayOf('"', '\''), startQuote + 1)
        if (endQuote == -1) return ""
        
        return json.substring(startQuote + 1, endQuote)
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