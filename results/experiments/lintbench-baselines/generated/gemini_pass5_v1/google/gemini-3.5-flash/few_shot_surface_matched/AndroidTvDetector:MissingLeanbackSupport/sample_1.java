package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackSupport",
                    "Missing Leanback Support",
                    "The manifest should declare the use of the Leanback user interface required by Android TV. "
                            + "To fix this, add `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` "
                            + "to your manifest.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String NODE_MANIFEST = "manifest";
    private static final String NODE_USES_FEATURE = "uses-feature";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";

    private boolean mHasLeanback;
    private Element mManifestElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_MANIFEST, NODE_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mHasLeanback = false;
        mManifestElement = null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if (NODE_MANIFEST.equals(tagName)) {
            mManifestElement = element;
        } else if (NODE_USES_FEATURE.equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if ("android.software.leanback".equals(name)) {
                mHasLeanback = true;
            }
        }
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        if (!mHasLeanback && mManifestElement != null) {
            context.report(
                    ISSUE,
                    mManifestElement,
                    context.getLocation(mManifestElement),
                    "The manifest should declare the use of the Leanback user interface required by Android TV.");
        }
    }
}