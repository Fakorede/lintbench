package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*
import java.util.*

class IndentationDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        val SUSPICIOUS_INDENTATION = Issue.create(
            "SuspiciousIndentation",
            "Suspicious indentation suggests a grouping that isn't actually there in the code.",
            "This check looks for cases where the indentation suggests a grouping that isn't actually there in the code. A common example of this would be something like:\n" +
                    "\n" +
                    "