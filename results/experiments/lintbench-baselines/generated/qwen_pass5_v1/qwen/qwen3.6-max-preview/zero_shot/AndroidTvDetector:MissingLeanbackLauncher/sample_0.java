package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
        "MissingLeanbackLauncher",
        "Missing Leanback Launcher Intent Filter",
        "An application intended to run on TV devices must declare a launcher activity for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` intent filter.\n\n" +
        "Reference documentation:\n" +
        "  - https://developer.android.com/training/tv/start/start.html#tv-activity",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String LEANBACK_FEATURE = "android.software.leanback";
    private static final String LEANBACK_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";

    private static final String KEY_IS_TV = "isTv";
    private static final String KEY_HAS_LAUNCHER = "hasLauncher";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(Context context) {
        context.putClientData(KEY_IS_TV, Boolean.FALSE);
        context.putClientData(KEY_HAS_LAUNCHER, Boolean.FALSE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        String name = element.getAttribute("android:name");

        if ("uses-feature".equals(tag)) {
            if (LEANBACK_FEATURE.equals(name)) {
                context.putClientData(KEY_IS_TV, Boolean.TRUE);
            }
        } else if ("category".equals(tag)) {
            if (LEANBACK_CATEGORY.equals(name)) {
                context.putClientData(KEY_HAS_LAUNCHER, Boolean.TRUE);
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        Boolean isTv = (Boolean) context.getClientData(KEY_IS_TV);
        Boolean hasLauncher = (Boolean) context.getClientData(KEY_HAS_LAUNCHER);

        if (Boolean.TRUE.equals(isTv) && !Boolean.TRUE.equals(hasLauncher)) {
            XmlContext xmlContext = (XmlContext) context;
            Element root = xmlContext.document.getDocumentElement();
            String message = "The manifest declares the leanback feature, but does not declare an activity with the LEANBACK_LAUNCHER intent filter. " +
                    "An application intended to run on TV devices must declare a launcher activity for TV in its manifest using a " +
                    "`android.intent.category.LEANBACK_LAUNCHER` intent filter.";
            xmlContext.report(ISSUE, xmlContext.getLocation(root), message);
        }
    }
}