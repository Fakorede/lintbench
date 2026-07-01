package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UImportStatement
import org.w3c.dom.Element
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner, XmlScanner {

    private var usesCredentialManager = false
    private var hasDalMetaData = false
    private var manifestContext: XmlContext? = null

    companion object {
        private const val CREDMAN_PACKAGE = "androidx.credentials"
        private const val DAL_META_DATA_NAME = "androidx.credentials.PROVIDER_DIGITAL_ASSET_LINKS"

        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = "When using password sign-in through Credential Manager, an asset statements string resource file " +
                    "that includes the `assetlinks.json` files to load must be declared in the manifest using a `<meta-data>` element.\n" +
                    "See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.ALL_JAVA_FILES, Scope.MANIFEST)
            )
        )
    }

    override fun beforeCheckProject(context: Context) {
        usesCredentialManager = false
        hasDalMetaData = false
        manifestContext = null
    }

    override fun getApplicableUastTypes() = listOf(UImportStatement::class.java)

    override fun visitImportStatement(context: JavaContext, node: UImportStatement) {
        val importRef = node.asSourceString()
        if (importRef.contains(CREDMAN_PACKAGE)) {
            usesCredentialManager = true
        }
    }

    override fun getApplicableXmlTags() = listOf(SdkConstants.TAG_MANIFEST, "meta-data")

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.file.name == SdkConstants.ANDROID_MANIFEST_XML) {
            when (element.tagName) {
                SdkConstants.TAG_MANIFEST -> {
                    if (manifestContext == null) {
                        manifestContext = context
                    }
                }
                "meta-data" -> {
                    val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                    if (name == DAL_META_DATA_NAME) {
                        hasDalMetaData = true
                    }
                }
            }
        }
    }

    override fun afterCheckProject(context: Context) {
        if (usesCredentialManager && !hasDalMetaData && manifestContext != null) {
            val location = Location.create(manifestContext!!.file)
            context.report(
                ISSUE,
                location,
                "Missing Digital Asset Links `<meta-data>` declaration for Credential Manager. " +
                        "Add `<meta-data android:name=\"$DAL_META_DATA_NAME\" android:resource=\"@xml/asset_statements\" />` to your manifest."
            )
        }
    }
}