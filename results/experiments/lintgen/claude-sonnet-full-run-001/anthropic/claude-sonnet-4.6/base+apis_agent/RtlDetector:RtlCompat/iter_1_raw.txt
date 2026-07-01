package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Collection;
import java.util.Collections;

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
            Severity.ERROR,
            new Implementation(
                    RtlDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final String ATTR_GRAVITY = "gravity";
    private static final String ATTR_LAYOUT_GRAVITY = "layout_gravity";

    public RtlDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        // Only relevant if minSdkVersion < 17
        int minSdk = context.getProject().getMinSdk();
        if (minSdk >= 17) {
            return;
        }

        // Check that the element also has a gravity or layout_gravity attribute
        Element element = attribute.getOwnerElement();
        NamedNodeMap attributes = element.getAttributes();

        boolean hasGravity = false;
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String localName = attr.getLocalName();
            if (localName == null) {
                localName = attr.getName();
                // Strip namespace prefix if present
                int colon = localName.indexOf(':');
                if (colon >= 0) {
                    localName = localName.substring(colon + 1);
                }
            }
            if (ATTR_GRAVITY.equals(localName) || ATTR_LAYOUT_GRAVITY.equals(localName)) {
                hasGravity = true;
                break;
            }
        }

        if (!hasGravity) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "To support older versions than API 17 (project specifies " + minSdk + ") " +
                    "you must also specify `gravity` or `layout_gravity` when specifying " +
                    "`textAlignment`"
            );
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        // Not used
    }
}