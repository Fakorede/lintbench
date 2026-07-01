package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_GRAVITY;

public class RtlDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "API 17 adds a `textAlignment` attribute to specify text alignment. However, " +
            "if you are supporting older versions than API 17, you must **also** specify a " +
            "gravity or layout_gravity attribute, since older platforms will ignore the " +
            "`textAlignment` attribute.",
            Category.RTL,
            6,
            Severity.WARNING,
            new Implementation(
                    RtlDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final String ATTR_TEXT_ALIGNMENT = "textAlignment";
    private static final int RTL_API = 17;

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Only care about android:textAlignment
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        if (!ATTR_TEXT_ALIGNMENT.equals(attribute.getLocalName())) {
            return;
        }

        // Check if the project's minSdkVersion is less than 17
        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk >= RTL_API) {
            // No compatibility issue needed
            return;
        }

        // Check whether the element also has a gravity or layout_gravity attribute
        Element element = attribute.getOwnerElement();
        NamedNodeMap attributes = element.getAttributes();

        boolean hasGravity = false;
        boolean hasLayoutGravity = false;

        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            if (ANDROID_URI.equals(attr.getNamespaceURI())) {
                String localName = attr.getLocalName();
                if (ATTR_GRAVITY.equals(localName)) {
                    hasGravity = true;
                } else if (ATTR_LAYOUT_GRAVITY.equals(localName)) {
                    hasLayoutGravity = true;
                }
            }
        }

        if (!hasGravity && !hasLayoutGravity) {
            String message = String.format(
                    "To support older versions than API 17 (project specifies %1$d) " +
                    "you should also specify `gravity` or `layout_gravity` when using " +
                    "`textAlignment` attribute",
                    minSdk
            );
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }
}