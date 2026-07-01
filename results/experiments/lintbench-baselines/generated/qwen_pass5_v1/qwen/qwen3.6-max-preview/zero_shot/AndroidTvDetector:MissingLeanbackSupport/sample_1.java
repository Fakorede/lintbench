package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends ResourceXmlDetector {

    private static final String LEANBACK_FEATURE = "android.software.leanback";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackSupport",
            "Missing Leanback Support",
            "The manifest should declare the use of the Leanback user interface required by Android TV.\n\n" +
                    "To fix this, add\n" +
                    "`<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />`\n" +
                    "to your manifest.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST)
    );

    private boolean hasLeanback = false;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        hasLeanback = false;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (LEANBACK_FEATURE.equals(name)) {
            hasLeanback = true;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!hasLeanback) {
            XmlContext xmlContext = (XmlContext) context;
            Element root = xmlContext.getDocument().getDocumentElement();
            xmlContext.report(ISSUE, xmlContext.getLocation(root),
                    "The manifest should declare the use of the Leanback user interface required by Android TV");
        }
    }
}