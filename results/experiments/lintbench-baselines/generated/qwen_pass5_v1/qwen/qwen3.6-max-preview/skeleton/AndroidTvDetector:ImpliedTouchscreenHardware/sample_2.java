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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "Apps require the `android.hardware.touchscreen` feature by default. " +
                    "If you want your app to be available on TV, you must also explicitly declare " +
                    "that a touchscreen is not required as follows: " +
                    "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mTouchscreenOptional;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mTouchscreenOptional = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mTouchscreenOptional && context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            Element root = xmlContext.document.getDocumentElement();
            if (root != null && "manifest".equals(root.getTagName())) {
                xmlContext.report(ISSUE, root, xmlContext.getLocation(root),
                        "Apps require the `android.hardware.touchscreen` feature by default. " +
                        "If you want your app to be available on TV, you must explicitly declare " +
                        "that a touchscreen is not required as follows: " +
                        "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`");
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("android:name");
        String required = element.getAttribute("android:required");
        if ("android.hardware.touchscreen".equals(name) && "false".equals(required)) {
            mTouchscreenOptional = true;
        }
    }
}