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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_GRAVITY;

/**
 * Checks for RTL (right-to-left) compatibility issues.
 */
public class RtlDetector extends LayoutDetector {

    private static final String ATTR_TEXT_ALIGNMENT = "textAlignment";

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
        // Only care about textAlignment in the Android namespace
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        // Check if the project's minSdkVersion is less than 17
        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk >= 17) {
            // No compatibility issue; textAlignment is fully supported
            return;
        }

        // Check if the element also has a gravity or layout_gravity attribute
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
                    "you must also specify `gravity` or `layout_gravity` when specifying " +
                    "`textAlignment`",
                    minSdk
            );
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }
}