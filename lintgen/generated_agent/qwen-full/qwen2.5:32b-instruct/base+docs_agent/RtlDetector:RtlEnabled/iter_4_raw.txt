package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class RtlDetector extends Detector implements Detector.XmlScanner {
    private static final String SUPPORTS_RTL = "android:supportsRtl";
    private static final String APPLICATION_ELEMENT = "application";

    @NonNull
    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList(APPLICATION_ELEMENT);
    }

    @NonNull
    @Override
    public Scope getScope() {
        return Scope.MANIFEST_SCOPE;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element.getTagName().equals(APPLICATION_ELEMENT)) {
            Attr supportsRtlAttr = element.getAttributeNode(SUPPORTS_RTL);
            if (supportsRtlAttr == null || !supportsRtlAttr.getValue().equalsIgnoreCase("true")) {
                String message = "Using RTL attributes without enabling RTL support. "
                        + "Set android:supportsRtl=\"true\" in the <application> element.";
                context.report(ISSUE, element,
                        message);
            }
        }
    }

    public static final Issue ISSUE = Issue.create(
            "MissingSupportsRtl",
            "Using RTL attributes without enabling RTL support",
            "To enable right-to-left support, when running on API 17 and higher, you must set the `android:supportsRtl` attribute in the manifest `<application>` element. If you have started adding RTL attributes, but have not yet finished the migration, you can set the attribute to false to satisfy this lint check.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.MANIFEST_SCOPE));
}