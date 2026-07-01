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
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue MISSING_LEANBACK_SUPPORT =
            Issue.create(
                    "MissingLeanbackSupport",
                    "Missing Leanback Support",
                    "The manifest should declare the use of the Leanback user interface "
                            + "required by Android TV.\n\n"
                            + "To fix this, add\n"
                            + "```xml\n"
                            + "`<uses-feature android:name=\"android.software.leanback\"\n"
                            + "               android:required=\"false\" />`\n"
                            + "```\n"
                            + "to your manifest.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "android:name";
    private static final String LEANBACK_FEATURE = "android.software.leanback";
    private static final String NODE_MANIFEST = "manifest";

    private boolean mHasLeanbackFeature;
    private boolean mIsAndroidTvApp;
    private Element mManifestElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(USES_FEATURE, NODE_MANIFEST);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasLeanbackFeature = false;
        mIsAndroidTvApp = false;
        mManifestElement = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (NODE_MANIFEST.equals(tagName)) {
            mManifestElement = element;
        } else if (USES_FEATURE.equals(tagName)) {
            String name = element.getAttribute(ATTR_NAME);
            if (LEANBACK_FEATURE.equals(name)) {
                mHasLeanbackFeature = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mHasLeanbackFeature && mManifestElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    MISSING_LEANBACK_SUPPORT,
                    mManifestElement,
                    xmlContext.getLocation(mManifestElement),
                    "Manifest should declare the use of the Leanback user interface with "
                            + "`<uses-feature android:name=\"android.software.leanback\" "
                            + "android:required=\"false\" />`");
        }
    }
}