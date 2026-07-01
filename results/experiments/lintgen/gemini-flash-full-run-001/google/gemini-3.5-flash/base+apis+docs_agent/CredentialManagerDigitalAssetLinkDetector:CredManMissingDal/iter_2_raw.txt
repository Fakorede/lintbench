package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
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
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner, XmlScanner {

    private var passwordCredentialUsageLocation: Location? = null
    private var metaDataElementLocation: Location? = null
    private var metaDataResourceValue: String? = null
    private var metaDataHasResourceAttribute = false

    class StringResourceInfo(val name: String, val value: String, val location: Location)
    private val stringResources = mutableListOf<StringResourceInfo>()

    override fun beforeCheckEachProject(context: Context) {
        passwordCredentialUsageLocation = null
        metaDataElementLocation = null
        metaDataResourceValue = null
        metaDataHasResourceAttribute = false
        stringResources.clear()
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            "androidx.credentials.CreatePasswordRequest",
            "androidx.credentials.CreatePasswordRequest.Builder",
            "androidx.credentials.GetPasswordOption",
            "androidx.credentials.GetPasswordOption.Builder",
            "androidx.credentials.PasswordCredential"
        )
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        if (passwordCredentialUsageLocation == null) {
            passwordCredentialUsageLocation = context.getLocation(node)
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("meta-data", "string")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        if (tagName == "meta-data") {
            val name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name")
            if (name == "asset_statements") {
                metaDataElementLocation = context.getLocation(element)
                if (element.hasAttributeNS(SdkConstants.ANDROID_URI, "resource")) {
                    metaDataHasResourceAttribute = true
                    val resource = element.getAttributeNS(SdkConstants.ANDROID_URI, "resource")
                    metaDataResourceValue = resource.substringAfter("@string/")
                } else {
                    metaDataHasResourceAttribute = false
                }
            }
        } else if (tagName == "string") {
            val name = element.getAttribute("name")
            val value = element.textContent
            val location = context.getLocation(element)
            stringResources.add(StringResourceInfo(name, value, location))
        }
    }

    override fun afterCheckEachProject(context: Context) {
        val isUsingCredMan = passwordCredentialUsageLocation != null
        val hasMetaData = metaDataElementLocation != null

        if (isUsingCredMan && !hasMetaData) {
            context.report(
                ISSUE,
                passwordCredentialUsageLocation!!,
                "Missing Digital Asset Link declaration in AndroidManifest.xml for Credential Manager password sign-in"
            )
        } else if (hasMetaData) {
            val metaDataLoc = metaDataElementLocation!!
            if (!metaDataHasResourceAttribute) {
                context.report(
                    ISSUE,
                    metaDataLoc,
                    "The `<meta-data>` element for `asset_statements` must define an `android:resource` attribute"
                )
            } else {
                val resName = metaDataResourceValue
                if (resName != null) {
                    val matchingStrings = stringResources.filter { it.name == resName }
                    for (strInfo in matchingStrings) {
                        val content = strInfo.value
                        if (!content.contains("\"include\"") && !content.contains("'include'")) {
                            context.report(
                                ISSUE,
                                strInfo.location,
                                "The asset statements string resource must contain an `include` statement pointing to the `assetlinks.json` file"
                            )
                        } else {
                            val includeRegex = """["']include["']\s*:\s*["']([^"']*)["']""".toRegex()
                            val match = includeRegex.find(content)
                            if (match != null) {
                                val url = match.groupValues[1]
                                val cleanUrl = url.replace("\\/", "/")
                                if (cleanUrl.isEmpty() || !cleanUrl.startsWith("https://") || !cleanUrl.endsWith("assetlinks.json")) {
                                    context.report(
                                        ISSUE,
                                        strInfo.location,
                                        "The asset statements `include` must point to a valid HTTPS URL ending with `assetlinks.json`"
                                    )
                                }
                            } else {
                                context.report(
                                    ISSUE,
                                    strInfo.location,
                                    "The asset statements `include` must point to a valid HTTPS URL ending with `assetlinks.json`"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements string \
                resource file that includes the `assetlinks.json` files to load must be declared \
                in the manifest using a `<meta-data>` element.
                """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST, Scope.RESOURCE_FILE)
            )
        )
    }
}