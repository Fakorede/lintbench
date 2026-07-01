package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner, XmlScanner {

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
                Scope.JAVA_FILE_SCOPE,
                Scope.MANIFEST_SCOPE,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val GET_PASSWORD_REQUEST = "androidx.credentials.GetPasswordRequest"
        private const val GET_CREDENTIAL_REQUEST = "androidx.credentials.GetCredentialRequest"
        private const val ASSET_STATEMENTS_NAME = "asset_statements"

        private val projectState = mutableMapOf<String, State>()
        private fun getState(context: Context): State {
            return projectState.getOrPut(context.project.dir.toString()) { State() }
        }
        private fun clearState(context: Context) {
            projectState.remove(context.project.dir.toString())
        }
    }

    private class State {
        var hasCredManUsage = false
        var applicationLocation: Location? = null
        var hasMetaData = false
        var hasResourceAttr = false
        var metaDataLocation: Location? = null
        var stringContent: String? = null
        var stringLocation: Location? = null
    }

    override fun getApplicableConstructorTypes(): List<String>? = listOf(GET_PASSWORD_REQUEST, GET_CREDENTIAL_REQUEST)

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        getState(context).hasCredManUsage = true
    }

    override fun getApplicableElements(): Collection<String>? = listOf(SdkConstants.TAG_APPLICATION, SdkConstants.TAG_META_DATA, SdkConstants.TAG_STRING)

    override fun visitElement(context: XmlContext, element: Element) {
        val state = getState(context)
        val fileName = context.file.name

        if (fileName == SdkConstants.ANDROID_MANIFEST_XML) {
            when (element.tagName) {
                SdkConstants.TAG_APPLICATION -> {
                    if (state.applicationLocation == null) {
                        state.applicationLocation = context.getLocation(element)
                    }
                }
                SdkConstants.TAG_META_DATA -> {
                    val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                    if (name == ASSET_STATEMENTS_NAME) {
                        state.hasMetaData = true
                        state.metaDataLocation = context.getLocation(element)
                        val resource = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_RESOURCE)
                        if (resource.isNotEmpty()) {
                            state.hasResourceAttr = true
                        }
                    }
                }
            }
        } else if (element.tagName == SdkConstants.TAG_STRING) {
            val name = element.getAttribute(SdkConstants.ATTR_NAME)
            if (name == ASSET_STATEMENTS_NAME) {
                state.stringContent = element.textContent
                state.stringLocation = context.getLocation(element)
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        val state = getState(context)
        if (state.applicationLocation == null && state.metaDataLocation == null && state.stringLocation == null) {
            clearState(context)
            return
        }

        if (!state.hasMetaData) {
            context.report(
                ISSUE,
                state.applicationLocation!!,
                "Missing Digital Asset Links declaration for Credential Manager password sign-in. " +
                        "Add `<meta-data android:name=\"asset_statements\" android:resource=\"@string/asset_statements\" />` " +
                        "to your AndroidManifest.xml <application> tag."
            )
        } else if (!state.hasResourceAttr) {
            context.report(
                ISSUE,
                state.metaDataLocation!!,
                "Missing `android:resource` attribute on `<meta-data android:name=\"asset_statements\">`."
            )
        } else if (state.stringContent == null) {
            context.report(
                ISSUE,
                state.metaDataLocation!!,
                "Referenced `asset_statements` string resource not found."
            )
        } else {
            val content = state.stringContent ?: ""
            if (!content.contains("include")) {
                context.report(
                    ISSUE,
                    state.stringLocation!!,
                    "Missing `include` directive in `asset_statements` string resource."
                )
            } else if (!content.contains("https://")) {
                context.report(
                    ISSUE,
                    state.stringLocation!!,
                    "Missing URL in `asset_statements` string resource."
                )
            }
        }
        clearState(context)
    }
}