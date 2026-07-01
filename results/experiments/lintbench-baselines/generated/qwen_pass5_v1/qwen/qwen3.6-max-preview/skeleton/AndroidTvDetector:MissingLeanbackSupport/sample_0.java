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
import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LEANBACK_FEATURE = "android.software.leanback";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackSupport",
                    "Missing Leanback Support",
                    "The manifest should declare the use of the Leanback user interface required by Android TV.\n\n" +
                    "To fix this, add\n" +
                    "`<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />`\n" +
                    "to your manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean isManifest;
    private boolean hasLeanback;
    private Element manifestElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("manifest", "uses-feature");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        isManifest = context.file.getName().equals("AndroidManifest.xml");
        hasLeanback = false;
        manifestElement = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!isManifest) {
            return;
        }
        String tag = element.getTagName();
        if ("manifest".equals(tag)) {
            manifestElement = element;
        } else if ("uses-feature".equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if (LEANBACK_FEATURE.equals(name)) {
                hasLeanback = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (isManifest && !hasLeanback && manifestElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    xmlContext.getLocation(manifestElement),
                    "The manifest should declare the use of the Leanback user interface required by Android TV. " +
                    "To fix this, add `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` to your manifest."
            );
        }
    }
}