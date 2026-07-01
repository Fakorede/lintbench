package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.ANDROID_URI
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
import com.android.tools.lint.detector.api.XmlContext
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Element

class CredentialManagerDigitalAssetLinkDetector : Detector(), Detector.XmlScanner, Detector.SourceCodeScanner {

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, you must declare an asset statements string resource in the manifest using a `<meta-data>` element. The string resource should include the `assetlinks.json` files to load so the app can verify its association with the website.

                See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.JAVA_AND_RESOURCE_FILES
            )
        )
    }

    private var hasPasswordCredentialUsage = false
    private var hasAssetStatementsMetaData = false
    private var hasAssetStatementsString = false
    private var assetStatementStringName: String? = null
    private var usageLocation: Location? = null
    private var manifestLocation: Location? = null

    override fun beforeCheckEachProject(context: Context) {
        reset()
    }

    private fun reset() {
        hasPasswordCredentialUsage = false
        hasAssetStatementsMetaData = false
        hasAssetStatementsString = false
        assetStatementStringName = null
        usageLocation = null
        manifestLocation = null
    }

    override fun getApplicableElements(): Collection<String> = listOf("meta-data", "string", "item")

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.file.name == ANDROID_MANIFEST_XML) {
            if (manifestLocation == null) {
                manifestLocation = Location.create(context.file)
            }
            if (element.tagName == "meta-data") {
                visitMetaData(element)
            }
        } else {
            if (element.tagName == "string" || element.tagName == "item") {
                visitStringResource(element)
            }
        }
    }

    private fun visitMetaData(element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, "name")
        if (name != "asset_statements") return

        val resource = element.getAttributeNS(ANDROID_URI, "resource")
        if (resource.isNotEmpty() && resource.startsWith("@")) {
            hasAssetStatementsMetaData = true
            assetStatementStringName = resource.substringAfter("/")
        }
    }

    private fun visitStringResource(element: Element) {
        if (element.tagName == "item") {
            val type = element.getAttribute("type")
            if (type != "string") return
        }

        val name = element.getAttribute("name")
        if (name.isEmpty()) return

        val isTargetString = name == assetStatementStringName || name == "asset_statements"
        if (!isTargetString) return

        val text = element.textContent
        if (text.contains("assetlinks.json", ignoreCase = true)) {
            hasAssetStatementsString = true
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UCallExpression::class.java, UReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                checkCredentialManagerUsage(context, node)
            }

            override fun visitReferenceExpression(node: UReferenceExpression) {
                checkCredentialManagerUsage(context, node)
            }
        }
    }

    private fun checkCredentialManagerUsage(context: JavaContext, node: UElement) {
        val candidates = mutableListOf<String?>()

        if (node is UCallExpression) {
            candidates.add(node.methodName)
            candidates.add(node.classReference?.resolvedName)
            val resolved = node.resolve()
            candidates.add(resolved?.returnType?.canonicalText)
            candidates.add(resolved?.containingClass?.qualifiedName)
        }

        if (node is UReferenceExpression) {
            candidates.add(node.resolvedName)
            candidates.add(node.getExpressionType()?.canonicalText)
        }

        for (candidate in candidates) {
            if (!candidate.isNullOrBlank() &&
                (candidate.contains("GetPasswordOption") ||
                 candidate.contains("CreatePasswordRequest") ||
                 candidate.contains("PasswordCredential"))
            ) {
                hasPasswordCredentialUsage = true
                if (usageLocation == null) {
                    usageLocation = context.getLocation(node)
                }
                break
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (!hasPasswordCredentialUsage) return
        if (hasAssetStatementsMetaData && hasAssetStatementsString) return

        val location = usageLocation ?: manifestLocation ?: Location.create(context.file)
        val message = buildString {
            append("Missing Digital Asset Link declaration for Credential Manager password sign-in. ")
            if (!hasAssetStatementsMetaData) {
                append("Add a `<meta-data android:name=\"asset_statements\" android:resource=\"@string/asset_statements\" />` element to the application in `AndroidManifest.xml`. ")
            }
            if (!hasAssetStatementsString) {
                append("The `asset_statements` string resource must include the `assetlinks.json` files to load.")
            }
        }

        context.report(ISSUE, location, message)
    }
}