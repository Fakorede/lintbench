package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends ResourceXmlDetector {
    private boolean mHasLeanback;

    public static final Issue ISSUE = Issue.create(
        "MissingLeanbackSupport",
        "Missing Leanback Support",
        "The manifest should declare the use of the Leanback user interface required by Android TV.\n\n" +
        "To fix this, add\n" +
        "`<uses-feature android:name=\"android.software.leanback\"\n" +
        "              android:required=\"false\" />`\n" +
        "to your manifest.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void beforeCheckFile(Context context) {
        mHasLeanback = false;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
        if ("android.software.leanback".equals(name)) {
            mHasLeanback = true;
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!mHasLeanback && context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            if (xmlContext.document != null) {
                Element root = xmlContext.document.getDocumentElement();
                if (root != null) {
                    xmlContext.report(ISSUE, xmlContext.getLocation(root),
                        "The manifest should declare the use of the Leanback user interface required by Android TV.");
                }
            }
        }
    }
}