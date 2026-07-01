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
import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackLauncher",
            "Missing Leanback Launcher Intent Filter",
            "An application intended to run on TV devices must declare a launcher activity for TV in its manifest using an android.intent.category.LEANBACK_LAUNCHER intent filter.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean hasLeanbackLauncher;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("category");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        hasLeanbackLauncher = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
            hasLeanbackLauncher = true;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!hasLeanbackLauncher && context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            Element root = xmlContext.document.getDocumentElement();
            if (root != null) {
                xmlContext.report(ISSUE, root, xmlContext.getLocation(root),
                        "Missing Leanback Launcher intent filter. TV apps must declare an activity with the LEANBACK_LAUNCHER category.");
            }
        }
    }
}