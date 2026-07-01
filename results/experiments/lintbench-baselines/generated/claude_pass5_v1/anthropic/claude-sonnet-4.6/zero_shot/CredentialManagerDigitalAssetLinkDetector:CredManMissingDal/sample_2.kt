package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_APPLICATION
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

/**
 * Lint detector that checks for missing Digital Asset Link (DAL) configuration
 * when using Credential Manager for password sign-in.
 *
 * When using password sign-in through Credential Manager, an asset statements
 * string resource file that includes the `assetlinks.json` files to load must
 * be declared in the manifest using a `<meta-data>` element.
 *
 * Reference: https://developer.android.com/identity/sign-in/credential-manager#add-support-dal
 */
class CredentialManagerDigitalAssetLinkDetector : Detector(), XmlScanner {

    companion object {
        /**
         * The meta-data name required for Digital Asset Link support in Credential Manager.
         */
        private const val ASSET_STATEMENTS_META_DATA_NAME =
            "asset_statements"

        /**
         * The fully qualified meta-data name for asset statements.
         */
        private const val ASSET_STATEMENTS_META_DATA_FULL_NAME =
            "androidx.credentials.asset_statements"

        /**
         * The credential manager service action used to detect Credential Manager usage.
         */
        private const val CREDENTIAL_MANAGER_SERVICE_ACTION =
            "androidx.credentials.provider.CredentialProviderService"

        /**
         * Tag name for meta-data elements in the manifest.
         */
        private const val TAG_META_DATA = "meta-data"

        /**
         * Tag name for service elements in the manifest.
         */
        private const val TAG_SERVICE = "service"

        /**
         * Tag name for intent-filter elements in the manifest.
         */
        private const val TAG_INTENT_FILTER = "intent-filter"

        /**
         * Tag name for action elements in the manifest.
         */
        private const val TAG_ACTION = "action"

        /**
         * The issue reported when Digital Asset Link meta-data is missing.
         */
        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements \
                string resource file that includes the `assetlinks.json` files to load must be \
                declared in the manifest using a `<meta-data>` element.

                Add a `<meta-data>` element inside the `<application>` tag with the name \
                `androidx.credentials.asset_statements` pointing to your asset statements \
                string resource.

                See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal \
                for more details.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.MANIFEST_SCOPE
            ),
            moreInfo = "https://developer.android.com/identity/sign-in/credential-manager#add-support-dal"
        )
    }

    /** Whether a Credential Manager-related service has been found in the manifest. */
    private var credentialManagerServiceFound = false

    /** Whether the asset_statements meta-data has been found in the manifest. */
    private var assetStatementsMetaDataFound = false

    /** Location of the application element, used for reporting. */
    private var applicationElementLocation: Location? = null

    /** The application element itself, used for reporting. */
    private var applicationElement: Element? = null

    /** The XmlContext for the manifest file. */
    private var xmlContext: XmlContext? = null

    override fun beforeCheckFile(context: Context) {
        credentialManagerServiceFound = false
        assetStatementsMetaDataFound = false
        applicationElementLocation = null
        applicationElement = null
        xmlContext = null
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_APPLICATION, TAG_META_DATA, TAG_SERVICE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        xmlContext = context
        when (element.tagName) {
            TAG_APPLICATION -> {
                applicationElement = element
                applicationElementLocation = context.getLocation(element)
                // Check for asset_statements meta-data directly within application element
                checkForAssetStatementsInApplication(element)
            }
            TAG_META_DATA -> {
                val nameAttr = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: ""
                if (isAssetStatementsMetaData(nameAttr)) {
                    val valueAttr = element.getAttributeNS(ANDROID_URI, ATTR_VALUE) ?: ""
                    val resourceAttr = element.getAttributeNS(ANDROID_URI, "resource") ?: ""
                    if (valueAttr.isNotEmpty() || resourceAttr.isNotEmpty()) {
                        assetStatementsMetaDataFound = true
                    }
                }
            }
            TAG_SERVICE -> {
                if (isCredentialManagerService(element)) {
                    credentialManagerServiceFound = true
                }
            }
        }
    }

    /**
     * Checks if the given meta-data name corresponds to asset statements configuration.
     */
    private fun isAssetStatementsMetaData(name: String): Boolean {
        return name == ASSET_STATEMENTS_META_DATA_FULL_NAME ||
                name == ASSET_STATEMENTS_META_DATA_NAME ||
                name.endsWith(".asset_statements")
    }

    /**
     * Checks for asset_statements meta-data directly within the application element.
     */
    private fun checkForAssetStatementsInApplication(applicationElement: Element) {
        val children = applicationElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_META_DATA) {
                val nameAttr = child.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: ""
                if (isAssetStatementsMetaData(nameAttr)) {
                    val valueAttr = child.getAttributeNS(ANDROID_URI, ATTR_VALUE) ?: ""
                    val resourceAttr = child.getAttributeNS(ANDROID_URI, "resource") ?: ""
                    if (valueAttr.isNotEmpty() || resourceAttr.isNotEmpty()) {
                        assetStatementsMetaDataFound = true
                    }
                }
            }
        }
    }

    /**
     * Checks if the given service element is a Credential Manager provider service.
     */
    private fun isCredentialManagerService(serviceElement: Element): Boolean {
        val children = serviceElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_INTENT_FILTER) {
                if (intentFilterHasCredentialManagerAction(child)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Checks if the given intent-filter element contains a Credential Manager action.
     */
    private fun intentFilterHasCredentialManagerAction(intentFilterElement: Element): Boolean {
        val children = intentFilterElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_ACTION) {
                val actionName = child.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: ""
                if (actionName == CREDENTIAL_MANAGER_SERVICE_ACTION) {
                    return true
                }
            }
        }
        return false
    }

    override fun afterCheckFile(context: Context) {
        if (credentialManagerServiceFound && !assetStatementsMetaDataFound) {
            val location = applicationElementLocation
            val appElement = applicationElement
            val ctx = xmlContext

            if (ctx != null) {
                val reportLocation = if (appElement != null) {
                    ctx.getLocation(appElement)
                } else {
                    location ?: Location.create(context.file)
                }

                ctx.report(
                    issue = ISSUE,
                    location = reportLocation,
                    message = "Missing `asset_statements` `<meta-data>` element in `<application>`. " +
                            "When using Credential Manager for password sign-in, you must declare " +
                            "an asset statements resource using " +
                            "`<meta-data android:name=\"androidx.credentials.asset_statements\" " +
                            "android:resource=\"@string/asset_statements\" />`."
                )
            }
        }
    }
}