package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String LEANBACK_FEATURE = "android.software.leanback";
    private static final String LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String ATTR_CATEGORY = "category";
    private static final String NODE_INTENT = "intent-filter";
    private static final String NODE_CATEGORY = "category";
    private static final String NODE_USES_INTENT = "uses-intent";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackSupport",
                    "Missing Leanback Support",
                    "The manifest should declare the use of the Leanback user interface "
                            + "required by Android TV.\n\n"
                            + "To fix this, add\n"
                            + "