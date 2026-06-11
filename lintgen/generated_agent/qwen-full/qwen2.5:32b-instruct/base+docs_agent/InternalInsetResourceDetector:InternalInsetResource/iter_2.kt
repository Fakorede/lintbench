package com.android.tools.lint.checks

import com.android.SdkConstants.*
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class InternalInsetResourceDetector : Detector(), ResourceXmlScanner {
    companion object Issues {
        val USE_INNER_INSETS = Issue.create(
            id = "UseInnerInsets",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                Using internal inset dimension resources is not a supported way to retrieve the relevant insets for your application. The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI.
                
                To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("dimen")
    }

    override fun visitResource(resourceType: String, name: String, file: File, element: Element) {
        if (resourceType == "dimen" && name.contains("inner_inset")) {
            val location = Location.create(file, element)
            context.report(
                USE_INNER_INSETS,
                location,
                "Using internal inset dimension resources is not a supported way to retrieve the relevant insets for your application. Use `androidx.core.view.WindowInsetsCompat` and related APIs instead."
            )
        }
    }

    override fun afterCheckProject(context: Context) {
        super.afterCheckProject(context)
    }
}