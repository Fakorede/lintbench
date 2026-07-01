package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class EllipsizeMaxLinesDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_MAX_LINES = "maxLines";
    private static final String ATTR_ELLIPSIZE = "ellipsize";

    public static final Issue ISSUE = Issue.create(
            "EllipsizeMaxLines",
            "Combining ellipsize with maxLines=1",
            "Combining `ellipsize` and `maxLines=\"1\"` can lead to crashes on some devices. "
                + "Do not replace `singleLine=\"true\"` with `maxLines=\"1\"` when using `ellipsize`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_MAX_LINES, ATTR_ELLIPSIZE);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!ATTR_MAX_LINES.equals(attribute.getLocalName())) {
            return;
        }

        if (!"1".equals(attribute.getValue())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        String ellipsize = element.getAttributeNS(ANDROID_URI, ATTR_ELLIPSIZE);
        if (!ellipsize.isEmpty()) {
            Location location = context.getLocation(attribute);
            context.report(
                    ISSUE,
                    attribute,
                    location,
                    "Combining ellipsize with maxLines=1 can cause crashes on some devices; "
                        + "use singleLine=\"true\" instead of maxLines=\"1\" when ellipsizing.");
        }
    }
}