package com.android.tools.lint.checks

import com.android.SdkConstants.*
import com.android.annotations.VisibleForTesting
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*

class AppCompatCustomViewDetector : Detector(), SourceCodeScanner {

    companion object {
        private val ISSUE = Issue.create(
            id = "AppCompatCustomView",
            briefDescription = "This custom view should extend `androidx.appcompat.widget.AppCompatTextView` instead",
            explanation = """
                In order to support features such as tinting, the appcompat library will automatically load special appcompat replacements for the built-in widgets. However, this does not work for your own custom views.
                
                Instead of extending the `android.widget` classes directly, you should extend one of the delegate classes in `androidx.appcompat.widget.AppCompatTextView`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppCompatCustomViewDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val ANDROID_WIDGET_PACKAGE = "android.widget"
        private val APPCOMPAT_WIDGET_PACKAGE = "androidx.appcompat.widget.AppCompatTextView"

        @VisibleForTesting
        internal val TARGET_CLASSES = setOf(
            "Button",
            "EditText",
            "ImageButton",
            "ImageView",
            "Spinner",
            "TextView",
            "CheckBox",
            "RadioButton"
        )
    }

    override fun getApplicableClasses() =
        TARGET_CLASSES.map { "$ANDROID_WIDGET_PACKAGE.$it" }.toSet()

    override fun visitClass(context: JavaContext, klass: UastClass) {
        val className = klass.name ?: return
        if (TARGET_CLASSES.contains(className)) {
            val superClass = klass.uastSuperClass?.name ?: return

            if (!superClass.startsWith(APP_COMPAT_WIDGET_PACKAGE)) {
                context.report(
                    ISSUE,
                    context.getLocation(klass),
                    "This custom view should extend `$APPCOMPAT_WIDGET_PACKAGE` instead"
                )
            }
        }
    }

    companion object {
        @JvmField
        val CHECK = AppCompatCustomViewDetector()
    }
}