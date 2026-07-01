package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;

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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";

    private boolean mHasLeanbackFeature;
    private boolean mHasLeanbackLauncher;
    private Element mManifestElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("manifest", "uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(Context context) {
        mHasLeanbackFeature = false;
        mHasLeanbackLauncher = false;
        mManifestElement = null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if ("manifest".equals(tagName)) {
            mManifestElement = element;
        } else if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if ("android.software.leanback".equals(name)) {
                mHasLeanbackFeature = true;
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mHasLeanbackLauncher && !mHasLeanbackFeature) {
            if (context instanceof XmlContext) {
                XmlContext xmlContext = (XmlContext) context;
                Location location = mManifestElement != null
                        ? xmlContext.getLocation(mManifestElement)
                        : xmlContext.getLocation(xmlContext.document);
                xmlContext.report(
                        ISSUE,
                        mManifestElement,
                        location,
                        "Expects `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` tag in the manifest");
            }
        }
    }
}