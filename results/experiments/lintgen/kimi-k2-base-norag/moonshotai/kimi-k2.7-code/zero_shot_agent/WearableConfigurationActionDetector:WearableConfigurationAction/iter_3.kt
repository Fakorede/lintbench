package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val WEARABLE_CONFIGURATION_ACTION = "wearableConfigurationAction"
private const val WEARABLE_CONFIGURATION_ACTION_LEGACY =
    "com.google.android.wearable.watchface.WEARABLE_CONFIGURATION_ACTION"
private const val WATCH_FACE_EDITOR =