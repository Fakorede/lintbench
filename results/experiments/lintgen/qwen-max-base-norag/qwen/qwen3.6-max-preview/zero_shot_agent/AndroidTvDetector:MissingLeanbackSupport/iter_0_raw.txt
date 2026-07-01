package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.*;
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
            Element root = xmlContext.getDocument().getDocumentElement();
            if (root != null) {
                context.report(ISSUE, root, context.getLocation(root),
                    "The manifest should declare the use of the Leanback user interface required by Android TV.");
            }
        }
    }
}