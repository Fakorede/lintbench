package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UClass

class AppCompatCustomViewDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            "AppCompatCustomView",
            "This custom view should extend `androidx.appcompat.widget.AppCompatTextView` instead of `android.widget.TextView`.",
            "In order to support features such as tinting, the appcompat library will automatically load special appcompat replacements for the built-in widgets. However, this does not work for your own custom views. Instead of extending the `android.widget` classes directly, you should extend one of the delegate classes in `androidx.appcompat.widget.AppCompatTextView`.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            Implementation(AppCompatCustomViewDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val superClass = declaration.superClass ?: return

        val superClassName = superClass.qualifiedName
        if (superClassName?.startsWith("android.widget.") == true &&
            !superClassName.startsWith("androidx.appcompat.widget.AppCompat")
        ) {
            context.report(
                ISSUE,
                declaration,
                context.getLocation(declaration),
                "This custom view should extend `androidx.appcompat.widget.AppCompatTextView` instead of `$superClassName`."
            )
        }
    }
}