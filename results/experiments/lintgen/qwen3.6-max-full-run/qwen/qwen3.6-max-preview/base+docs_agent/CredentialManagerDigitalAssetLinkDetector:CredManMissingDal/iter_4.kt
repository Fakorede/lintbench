package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class CredentialManagerDigitalAssetLinkDetector : Detector(), Detector.UastScanner, Detector.XmlScanner {

    private var hasCredManUsage = false
    private var applicationLocation: Location? = null
    private var hasMetaData = false
    private var hasResourceAttr = false
    private var metaDataLocation: Location? = null
    private var stringContent: String? = null
    private var stringLocation: Location? = null

    companion object {
        @JvmField
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
            severity = Severity.ERROR,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.JAVA_FILE,
                Scope.MANIFEST,
                Scope.RESOURCE_FILE
            )
        )

        private const val GET_PASSWORD_REQUEST = "androidx.credentials.GetPasswordRequest"
        private const val GET_CREDENTIAL_REQUEST = "androidx.credentials.GetCredentialRequest"
        private const val ASSET_STATEMENTS_NAME = "asset_statements"
    }

    override fun beforeCheckEachProject(context: Context) {
        hasCredManUsage = false
        applicationLocation = null
        hasMetaData = false
        hasResourceAttr = false
        metaDataLocation = null
        stringContent = null
        stringLocation = null
    }

    override fun getApplicableConstructorTypes(): List<String>? = listOf(GET_PASSWORD_REQUEST, GET_CREDENTIAL_REQUEST)

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        hasCredManUsage = true
    }

    override fun getApplicableElements(): Collection<String>? = listOf(
        SdkConstants.TAG_APPLICATION,
        SdkConstants.TAG_META_DATA,
        SdkConstants.TAG_STRING
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val fileName = context.file.name
        if (fileName == SdkConstants.ANDROID_MANIFEST_XML) {
            when (element.tagName) {
                SdkConstants.TAG_APPLICATION -> {
                    if (applicationLocation == null) {
                        applicationLocation = context.getLocation(element)
                    }
                }
                SdkConstants.TAG_META_DATA -> {
                    val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                    if (name == ASSET_STATEMENTS_NAME) {
                        hasMetaData = true
                        metaDataLocation = context.getLocation(element)
                        val resource = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_RESOURCE)
                        if (resource.isNotEmpty()) {
                            hasResourceAttr = true
                        }
                    }
                }
            }
        } else if (element.tagName == SdkConstants.TAG_STRING) {
            val name = element.getAttribute(SdkConstants.ATTR_NAME)
            if (name == ASSET_STATEMENTS_NAME) {
                stringContent = element.textContent
                stringLocation = context.getLocation(element)
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (!hasCredManUsage) return

        if (!hasMetaData) {
            val location = applicationLocation ?: return
            context.report(
                ISSUE,
                location,
                "Missing Digital Asset Links declaration for Credential Manager password sign-in. " +
                        "Add `<meta-data android:name=\"asset_statements\" android:resource=\"@string/asset_statements\" />` " +
                        "to your AndroidManifest.xml <application> tag."
            )
        } else if (!hasResourceAttr) {
            context.report(
                ISSUE,
                metaDataLocation!!,
                "Missing `android:resource` attribute on `<meta-data android:name=\"asset_statements\">`."
            )
        } else if (stringContent == null) {
            context.report(
                ISSUE,
                metaDataLocation!!,
                "Referenced `asset_statements` string resource not found."
            )
        } else {
            val content = stringContent ?: ""
            if (!content.contains("include")) {
                context.report(
                    ISSUE,
                    stringLocation!!,
                    "Missing `include` directive in `asset_statements` string resource."
                )
            } else if (!content.contains("https://")) {
                context.report(
                    ISSUE,
                    stringLocation!!,
                    "Missing URL in `asset_statements` string resource."
                )
            }
        }
    }
}