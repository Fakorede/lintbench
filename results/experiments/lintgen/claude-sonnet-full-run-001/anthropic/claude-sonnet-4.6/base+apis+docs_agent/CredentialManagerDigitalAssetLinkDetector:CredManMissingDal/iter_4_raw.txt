package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_META_DATA
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), XmlScanner {

    companion object {
        private const val ASSET_STATEMENTS_META_DATA_NAME = "asset_statements"
        private const val ASSET_STATEMENTS_ANDROIDX_NAME = "androidx.credentials.ASSET_STATEMENTS"

        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements \
                string resource file that includes the `assetlinks.json` files to load must be \
                declared in the manifest using a `<meta-data>` element.

                Add a `<meta-data>` element inside `<application>` in your AndroidManifest.xml \
                that points to a string resource containing the Digital Asset Links JSON.

                See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal \
                for more details.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE),
                EnumSet.of(Scope.MANIFEST),
                EnumSet.of(Scope.RESOURCE_FILE)
            ),
            moreInfo = "https://developer.android.com/identity/sign-in/credential-manager#add-support-dal"
        )
    }

    // Per-project state
    private var hasAssetStatementsMetaData = false
    private var assetStatementsResourceName: String? = null
    private var applicationElement: Element? = null
    private var applicationContext: XmlContext? = null
    private var metaDataElement: Element? = null
    private var metaDataContext: XmlContext? = null

    // Map from string resource name to its value and element/context
    private val stringResources = mutableMapOf<String, Triple<String, Element, XmlContext>>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_APPLICATION, TAG_META_DATA, "string")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_APPLICATION -> {
                if (applicationElement == null) {
                    applicationElement = element
                    applicationContext = context
                }
            }
            TAG_META_DATA -> {
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return
                if (name == ASSET_STATEMENTS_META_DATA_NAME ||
                    name == ASSET_STATEMENTS_ANDROIDX_NAME
                ) {
                    hasAssetStatementsMetaData = true
                    metaDataElement = element
                    metaDataContext = context
                    val value = element.getAttributeNS(ANDROID_URI, ATTR_VALUE) ?: ""
                    // Extract resource name from @string/xxx
                    if (value.startsWith("@string/")) {
                        assetStatementsResourceName = value.removePrefix("@string/")
                    }
                }
            }
            "string" -> {
                val name = element.getAttribute("name") ?: return
                if (name.isNotEmpty()) {
                    val text = element.textContent ?: ""
                    stringResources[name] = Triple(text, element, context)
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        if (!hasAssetStatementsMetaData) {
            val appCtx = applicationContext
            val appElem = applicationElement
            val location = if (appCtx != null && appElem != null) {
                appCtx.getLocation(appElem)
            } else {
                Location.create(context.project.dir)
            }
            context.report(
                issue = ISSUE,
                location = location,
                message = "Missing `asset_statements` `<meta-data>` in the manifest. " +
                    "When using Credential Manager password sign-in, you must declare a " +
                    "`<meta-data android:name=\"asset_statements\" .../>` element inside " +
                    "`<application>` pointing to your Digital Asset Links JSON."
            )
            resetState()
            return
        }

        // Check the referenced string resource
        val resourceName = assetStatementsResourceName
        if (resourceName != null) {
            val triple = stringResources[resourceName]
            if (triple != null) {
                val (value, elem, ctx) = triple
                val trimmed = value.trim()
                val location = ctx.getLocation(elem)

                // Check for missing include
                if (!trimmed.contains("include") && !trimmed.contains("\"include\"")) {
                    context.report(
                        issue = ISSUE,
                        location = location,
                        message = "The `asset_statements` string resource is missing an `include` " +
                            "directive pointing to the `assetlinks.json` file."
                    )
                    resetState()
                    return
                }

                // Check for missing URL
                if (!trimmed.contains("http://") && !trimmed.contains("https://")) {
                    context.report(
                        issue = ISSUE,
                        location = location,
                        message = "The `asset_statements` string resource is missing a URL " +
                            "pointing to the `assetlinks.json` file."
                    )
                    resetState()
                    return
                }
            }
        }

        resetState()
    }

    private fun resetState() {
        hasAssetStatementsMetaData = false
        assetStatementsResourceName = null
        applicationElement = null
        applicationContext = null
        metaDataElement = null
        metaDataContext = null
        stringResources.clear()
    }
}