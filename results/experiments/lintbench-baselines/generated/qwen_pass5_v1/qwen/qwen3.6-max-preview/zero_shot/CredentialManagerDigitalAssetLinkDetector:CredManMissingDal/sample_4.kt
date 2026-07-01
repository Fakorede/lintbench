package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_META_DATA
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiClass
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Element

class CredentialManagerDigitalAssetLinkDetector : Detector(), Detector.UastScanner, Detector.XmlScanner {

    private var usesCredentialManager = false
    private var xmlContext: XmlContext? = null
    private var applicationElement: Element? = null
    private var hasAssetStatementsMetaData = false

    override fun beforeCheckProject(context: Context) {
        usesCredentialManager = false
        xmlContext = null
        applicationElement = null
        hasAssetStatementsMetaData = false
    }

    override fun getApplicableUastTypes() = listOf(UReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitReferenceExpression(node: UReferenceExpression) {
                val resolved = node.resolve()
                if (resolved is PsiClass) {
                    val fqn = resolved.qualifiedName
                    if (fqn == CREDENTIAL_MANAGER_FQN || fqn == CREDENTIAL_MANAGER_FQN_PLATFORM) {
                        usesCredentialManager = true
                    }
                }
            }
        }
    }

    override fun getApplicableElements() = listOf(TAG_APPLICATION)

    override fun visitElement(context: XmlContext, element: Element) {
        xmlContext = context
        applicationElement = element

        val metaDataList = element.getElementsByTagName(TAG_META_DATA)
        for (i in 0 until metaDataList.length) {
            val metaData = metaDataList.item(i) as Element
            val name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name == ASSET_STATEMENTS_META_DATA_NAME) {
                hasAssetStatementsMetaData = true
                break
            }
        }
    }

    override fun afterCheckProject(context: Context) {
        if (usesCredentialManager && !hasAssetStatementsMetaData && applicationElement != null && xmlContext != null) {
            xmlContext!!.report(
                ISSUE,
                applicationElement!!,
                xmlContext!!.getLocation(applicationElement!!),
                "Missing Digital Asset Link meta-data for Credential Manager. " +
                    "When using password sign-in through Credential Manager, you must declare " +
                    "an asset statements string resource in the manifest using " +
                    "`<meta-data android:name=\"asset_statements\" android:resource=\"@string/asset_statements\" />`."
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements \
                string resource file that includes the `assetlinks.json` files to load must be \
                declared in the manifest using a `<meta-data>` element.

                See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.JAVA_FILE,
                Scope.MANIFEST
            )
        )

        private const val ASSET_STATEMENTS_META_DATA_NAME = "asset_statements"
        private const val CREDENTIAL_MANAGER_FQN = "androidx.credentials.CredentialManager"
        private const val CREDENTIAL_MANAGER_FQN_PLATFORM = "android.credentials.CredentialManager"
    }
}