package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Arrays;
import java.util.Collection;

/**
 * Checks for RTL (right-to-left) compatibility issues in layout files.
 */
public class RtlDetector extends LayoutDetector {

    private static final String ATTR_TEXT_ALIGNMENT = "textAlignment";
    private static final String ATTR_GRAVITY = "gravity";
    private static final String ATTR_LAYOUT_GRAVITY = "layout_gravity";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final int RTL_API = 17;

    /** The main issue discovered by this detector */
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
                    Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link RtlDetector} */
    public RtlDetector() {
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Only care about textAlignment attributes in the Android namespace
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        // Check if the minSdkVersion is less than 17
        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk >= RTL_API) {
            // No compatibility issue; textAlignment is supported
            return;
        }

        // Check whether the element also has a gravity or layout_gravity attribute
        Element element = attribute.getOwnerElement();
        NamedNodeMap attributes = element.getAttributes();

        boolean hasGravity = false;
        boolean hasLayoutGravity = false;

        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            if (!ANDROID_URI.equals(attr.getNamespaceURI())) {
                continue;
            }
            String localName = attr.getLocalName();
            if (ATTR_GRAVITY.equals(localName)) {
                hasGravity = true;
            } else if (ATTR_LAYOUT_GRAVITY.equals(localName)) {
                hasLayoutGravity = true;
            }
        }

        if (!hasGravity && !hasLayoutGravity) {
            String message = String.format(
                    "To support older versions than API 17 (project specifies %1$d) " +
                    "you should also specify `gravity` or `layout_gravity` when using " +
                    "`textAlignment` attribute",
                    minSdk);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }
}