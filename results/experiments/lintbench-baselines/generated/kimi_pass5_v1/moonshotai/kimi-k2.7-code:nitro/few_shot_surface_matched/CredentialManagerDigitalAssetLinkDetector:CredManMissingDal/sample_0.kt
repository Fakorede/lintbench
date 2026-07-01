package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.DefaultPosition
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.PositionXmlParser
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val KEY_USED = "used"
    private const val KEY_FILE = "file"
    private const val KEY_START_OFFSET = "start"
    private const val KEY_END_OFFSET = "end"

    @JvmField
    val ISSUE =
      Issue.create(
        id = "CredManMissingDal",
        briefDescription = "Missing Digital Asset Link for Credential Manager",
        explanation =
          """
                When using password sign-in through Credential Manager, you must declare an \
                asset statements string resource in the manifest using a `<meta-data>` element. \
                Add the following inside the `<application>` element:

                <meta-data
                    android:name="asset_statements"
                    android:resource="@string/asset_statements" />

                The string resource must contain the Digital Asset Links to load. See \
                https://developer.android.com/identity/sign-in/credential-manager#add-support-dal.
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation =
          Implementation(
            CredentialManagerDigitalAssetLinkDetector::class.java,
            Scope.JAVA_FILE_SCOPE
          ),
        androidSpecific = true,
      )
  }

  override fun getApplicableConstructorTypes(): List<String> {
    return listOf("androidx.credentials.GetPasswordOption")
  }

  override fun visitConstructor(
    context: JavaContext,
    node: UCallExpression,
    constructor: PsiMethod
  ) {
    if (
      !context.evaluator.isMemberInClass(constructor, "androidx.credentials.GetPasswordOption")
    ) {
      return
    }

    val location = context.getLocation(node)
    val map = context.partialResults.map()
    map[KEY_USED] = true
    map[KEY_FILE] = context.file.absolutePath
    map[KEY_START_OFFSET] = location.start?.offset ?: 0
    map[KEY_END_OFFSET] = location.end?.offset ?: 0
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    // Partial results are aggregated and checked in afterCheckRootProject.
  }

  override fun afterCheckRootProject(context: Context) {
    val locations = mutableListOf<Location>()
    var hasUsage = false
    context.partialResults.accept { _, map ->
      if (map[KEY_USED] == true) {
        hasUsage = true
        val filePath = map[KEY_FILE] as? String ?: return@accept
        val startOffset = (map[KEY_START_OFFSET] as? Number)?.toInt() ?: 0
        val endOffset = (map[KEY_END_OFFSET] as? Number)?.toInt() ?: 0
        val file = java.io.File(filePath)
        locations.add(
          Location.create(
            file,
            DefaultPosition(0, 0, startOffset),
            DefaultPosition(0, 0, endOffset)
          )
        )
      }
    }
    if (!hasUsage || locations.isEmpty()) {
      return
    }

    val manifestFile = context.mainProject.manifest ?: return
    val document = PositionXmlParser.parse(manifestFile) as org.w3c.dom.Document
    val metaDataNodes = document.getElementsByTagName("meta-data")
    for (i in 0 until metaDataNodes.length) {
      val metaData = metaDataNodes.item(i) as? org.w3c.dom.Element ?: continue
      val name = metaData.getAttributeNS(ANDROID_URI, "name")
      val resource = metaData.getAttributeNS(ANDROID_URI, "resource")
      if (name == "asset_statements" && resource.startsWith("@string/")) {
        return
      }
    }

    val message =
      "Missing Digital Asset Link for Credential Manager password sign-in. " +
        "Add the asset_statements meta-data element to AndroidManifest.xml."
    for (location in locations) {
      context.report(Incident(ISSUE, location, message))
    }
  }
}