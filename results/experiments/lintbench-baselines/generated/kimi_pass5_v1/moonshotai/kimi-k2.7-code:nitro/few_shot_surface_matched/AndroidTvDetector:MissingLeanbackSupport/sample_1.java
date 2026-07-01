package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_USES_FEATURE;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackSupport",
                    "Missing Leanback Support",
                    "The manifest should declare the use of the Leanback user interface required by"
                            + " Android TV. Add `<uses-feature"
                            + " android:name=\"android.software.leanback\""
                            + " android:required=\"false\" />` to your manifest.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mHasLeanbackFeature;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mHasLeanbackFeature = false;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (TAG_USES_FEATURE.equals(element.getTagName())) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if ("android.software.leanback".equals(name)) {
                mHasLeanbackFeature = true;
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!mHasLeanbackFeature && context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            Element manifest = xmlContext.document.getDocumentElement();
            if (manifest != null) {
                xmlContext.report(
                        ISSUE,
                        manifest,
                        xmlContext.getLocation(manifest),
                        "You must add a `<uses-feature"
                                + " android:name=\"android.software.leanback\""
                                + " android:required=\"false\" />` element to your manifest for"
                                + " Android TV support.");
            }
        }
    }
}