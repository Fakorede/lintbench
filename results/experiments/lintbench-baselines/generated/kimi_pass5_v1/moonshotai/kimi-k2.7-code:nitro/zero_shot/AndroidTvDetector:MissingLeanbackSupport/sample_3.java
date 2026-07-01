package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public final class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String TAG_MANIFEST = "manifest";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String LEANBACK_FEATURE = "android.software.leanback";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackSupport",
            "Missing Leanback Support",
            "Android TV applications should declare the use of the Leanback user interface. "
                    + "Add `<uses-feature android:name=\"android.software.leanback\" "
                    + "android:required=\"false\" />` to the manifest.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_MANIFEST);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList features = element.getElementsByTagName(TAG_USES_FEATURE);
        boolean foundLeanback = false;

        for (int i = 0, n = features.getLength(); i < n; i++) {
            Element feature = (Element) features.item(i);
            String name = feature.getAttributeNS(ANDROID_NS, ATTR_NAME);
            if (name.isEmpty()) {
                name = feature.getAttribute(ATTR_NAME);
            }
            if (LEANBACK_FEATURE.equals(name)) {
                foundLeanback = true;
                break;
            }
        }

        if (!foundLeanback) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing `uses-feature` declaration for Android TV Leanback support"
            );
        }
    }
}