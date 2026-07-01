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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
    private static final String LEANBACK_FEATURE = "android.software.leanback";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackSupport",
                    "Missing Leanback Support",
                    "The manifest should declare the use of the Leanback user interface required by Android TV. Add `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` to the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasLeanbackFeature;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void beforeCheckFile(Context context) {
        mHasLeanbackFeature = false;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = getAttributeValue(element, "name");
        if (LEANBACK_FEATURE.equals(name)) {
            mHasLeanbackFeature = true;
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mHasLeanbackFeature) {
            return;
        }

        XmlContext xmlContext = (XmlContext) context;
        Element root = xmlContext.document.getDocumentElement();
        Location location =
                root != null ? xmlContext.getLocation(root) : xmlContext.getLocation(context.file);
        xmlContext.report(
                ISSUE,
                location,
                "Missing Leanback support: declare `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` in the manifest.");
    }

    private static String getAttributeValue(Element element, String localName) {
        if (element.hasAttributeNS(ANDROID_NAMESPACE, localName)) {
            return element.getAttributeNS(ANDROID_NAMESPACE, localName);
        }

        String prefixedName = "android:" + localName;
        if (element.hasAttribute(prefixedName)) {
            return element.getAttribute(prefixedName);
        }

        return null;
    }
}