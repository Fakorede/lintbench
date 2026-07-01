package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.ANDROID_URI
import com.android.resources.ResourceFolderType
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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Element
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), Detector.XmlScanner, SourceCodeScanner {

    companion object {
        @JvmField
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
                EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE, Scope.MANIFEST)
            )
        )

        private const val ASSET_STATEMENTS = "asset_statements"
        private const val RELATION = "delegate_permission/common.get_login_creds"
        private val URL_REGEX = Regex("https?://[^\\s\"]+")
    }

    private var hasPasswordCredentialUsage = false
    private var hasAssetStatementsMetaData = false
    private var hasResourceAttribute = false
    private var assetStatementResourceName: String? = null
    private var assetStatementValue: String? = null
    private var hasIncludeField = false
    private var hasValidUrl = false
    private var hasValidRelation = false
    private var usageLocation: Location? = null
    private var manifestLocation: Location? = null
    private var metaDataLocation: Location? = null
    private val stringResources = mutableMapOf<String, String>()

    override fun beforeCheckEachProject(context: Context) {
        reset()
    }

    private fun reset() {
        hasPasswordCredentialUsage = false
        hasAssetStatementsMetaData = false
        hasResourceAttribute = false
        assetStatementResourceName = null
        assetStatementValue = null
        hasIncludeField = false
        hasValidUrl = false
        hasValidRelation = false
        usageLocation = null
        manifestLocation = null
        metaDataLocation = null
        stringResources.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun getApplicableElements(): Collection<String> =
        listOf("meta-data", "string", "item")

    override fun visitElement(context: XmlContext, element: Element) {
        when {
            context.file.name == ANDROID_MANIFEST_XML -> visitManifestElement(context, element)
            else -> visitResourceElement(element)
        }
    }

    private fun visitManifestElement(context: XmlContext, element: Element) {
        if (manifestLocation == null) {
            manifestLocation = Location.create(context.file)
        }
        if (element.tagName != "meta-data") return

        val name = element.getAttributeNS(ANDROID_URI, "name")
        if (name != ASSET_STATEMENTS) return

        hasAssetStatementsMetaData = true
        metaDataLocation = context.getLocation(element)

        val resource = element.getAttributeNS(ANDROID_URI, "resource")
        if (resource.isNotEmpty()) {
            hasResourceAttribute = true
            if (resource.startsWith("@string/")) {
                assetStatementResourceName = resource.substring("@string/".length)
            }
        }

        val value = element.getAttributeNS(ANDROID_URI, "value")
        if (value.isNotEmpty()) {
            assetStatementValue = value
        }
    }

    private fun visitResourceElement(element: Element) {
        val tagName = element.tagName
        if (tagName == "item") {
            val type = element.getAttribute("type")
            if (type != "string") return
        } else if (tagName != "string") {
            return
        }

        val name = element.getAttribute("name")
        if (name.isEmpty()) return

        val content = element.textContent
        if (content.isNotBlank()) {
            stringResources[name] = content
        }
    }

    private fun validateAssetStatementContent(content: String) {
        val normalized = content.trim()
        if (normalized.contains("\"include\"", ignoreCase = true) ||
            normalized.contains("\"target\"", ignoreCase = true)
        ) {
            hasIncludeField = true
        }
        if (URL_REGEX.find(normalized) != null) {
            hasValidUrl = true
        }
        if (normalized.contains(RELATION, ignoreCase = true)) {
            hasValidRelation = true
        }
    }

    override fun getApplicableConstructorTypes(): List<String> =
        listOf(
            "androidx.credentials.GetPasswordOption",
            "androidx.credentials.CreatePasswordRequest",
            "androidx.credentials.PasswordCredential"
        )

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        hasPasswordCredentialUsage = true
        if (usageLocation == null) {
            usageLocation = context.getLocation(node)
        }
    }

    override fun getApplicableReferenceNames(): List<String> =
        listOf("GetPasswordOption", "CreatePasswordRequest", "PasswordCredential")

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement
    ) {
        hasPasswordCredentialUsage = true
        if (usageLocation == null) {
            usageLocation = context.getLocation(reference)
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (!hasPasswordCredentialUsage) return

        assetStatementValue?.let { validateAssetStatementContent(it) }

        val resourceName = assetStatementResourceName ?: ASSET_STATEMENTS
        stringResources[resourceName]?.let { validateAssetStatementContent(it) }

        if (hasAssetStatementsMetaData && hasResourceAttribute && hasIncludeField && hasValidUrl && hasValidRelation) {
            return
        }

        val location = usageLocation ?: metaDataLocation ?: manifestLocation ?: Location.create(context.file)
        val message = buildString {
            append("Missing Digital Asset Link declaration for Credential Manager password sign-in. ")
            when {
                !hasAssetStatementsMetaData -> {
                    append("Add a `<meta-data android:name=\\\"asset_statements\\\" android:resource=\\\"@string/asset_statements\\\" />` element to the `<application>` in `AndroidManifest.xml`.")
                }
                !hasResourceAttribute -> {
                    append("The `<meta-data android:name=\\\"asset_statements\\\" ...>` element must specify `android:resource=\\\"@string/asset_statements\\\"`.")
                }
                !hasIncludeField -> {
                    append("The `asset_statements` string resource must include an `include` or `target` field pointing to the `assetlinks.json` file.")
                }
                !hasValidUrl -> {
                    append("The `asset_statements` string resource must include a valid URL to the `assetlinks.json` file.")
                }
                else -> {
                    append("The `asset_statements` string resource must declare the relation `delegate_permission/common.get_login_creds`.")
                }
            }
        }

        context.report(ISSUE, location, message)
    }
}