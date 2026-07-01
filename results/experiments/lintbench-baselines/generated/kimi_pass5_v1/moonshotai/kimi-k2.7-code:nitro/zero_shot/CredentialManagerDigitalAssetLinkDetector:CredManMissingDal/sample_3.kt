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

class CredentialManagerDigitalAssetLinkDetector : Detector(), XmlScanner, SourceCodeScanner {

    private var usesCredentialManagerPassword = false
    private var hasAssetLinksMetaData = false
    private var manifestContext: XmlContext? = null
    private var applicationElement: Element? = null

    override fun beforeCheckProject(context: Context) {
        usesCredentialManagerPassword = false
        hasAssetLinksMetaData = false
        manifestContext = null
        applicationElement = null
    }

    override fun getApplicableElements(): Collection<String>? =
        listOf(SdkConstants.TAG_APPLICATION, SdkConstants.TAG_META_DATA)

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            SdkConstants.TAG_APPLICATION -> {
                manifestContext = context
                applicationElement = element
            }
            SdkConstants.TAG_META_DATA -> {
                val parent = element.parentNode as? Element
                if (parent?.tagName == SdkConstants.TAG_APPLICATION) {
                    val name = context.getManifestName(element)
                    val resource = element.getAttributeNS(
                        SdkConstants.ANDROID_URI,
                        SdkConstants.ATTR_RESOURCE
                    )
                    if (name == ASSET_LINKS_METADATA_NAME &&
                        resource?.startsWith(STRING_RESOURCE_PREFIX) == true
                    ) {
                        hasAssetLinksMetaData = true
                    }
                }
            }
        }
    }

    override fun getApplicableConstructorTypes(): List<String>? =
        listOf(PASSWORD_OPTION_FQN)

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        usesCredentialManagerPassword = true
    }

    override fun afterCheckProject(context: Context) {
        if (!usesCredentialManagerPassword || hasAssetLinksMetaData) {
            return
        }

        val location = applicationElement?.let { manifestContext?.getLocation(it) }
            ?: Location.create(context.project.manifestFile ?: context.file)

        context.report(
            ISSUE,
            location,
            "Missing Digital Asset Link declaration for Credential Manager password sign-in"
        )
    }

    companion object {
        private const val ASSET_LINKS_METADATA_NAME = "assetlinks"
        private const val PASSWORD_OPTION_FQN = "androidx.credentials.GetPasswordOption"
        private const val STRING_RESOURCE_PREFIX = "@string/"

        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, you must declare a Digital Asset Link (DAL) in the manifest using a <meta-data> element whose android:name is "assetlinks" and whose android:resource points to a string resource containing the asset link statements.

                For more information, see https://developer.android.com/identity/sign-in/credential-manager#add-support-dal.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.MANIFEST_SCOPE,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}