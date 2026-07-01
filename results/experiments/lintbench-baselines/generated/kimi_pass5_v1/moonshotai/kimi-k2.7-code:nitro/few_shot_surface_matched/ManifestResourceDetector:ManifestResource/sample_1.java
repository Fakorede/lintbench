package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_LABEL;
import static com.android.SdkConstants.ATTR_ROUND_ICON;
import static com.android.SdkConstants.TAG_APPLICATION;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceUrl;
import com.android.resources.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Attr;

public class ManifestResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Elements in the manifest can reference resources, but those resources cannot "
                            + "vary across configurations (except as a special case, by version, "
                            + "and except for a few specific package attributes such as the "
                            + "application title and icon).",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ALL);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null || url.isFramework() || url.isTheme() || url.isCreate()) {
            return;
        }

        if (url.type == ResourceType.ATTR) {
            return;
        }

        if (isApplicationTitleOrIcon(attribute)) {
            return;
        }

        List<ResourceItem> items = getResources(context.getProject(), url);
        if (items == null || items.isEmpty()) {
            return;
        }

        boolean hasVariation = false;
        boolean onlyVersion = true;
        for (ResourceItem item : items) {
            FolderConfiguration config = item.getSource().getFolderConfiguration();
            String qualifiers = config.getQualifierString();
            if (qualifiers == null || qualifiers.isEmpty()) {
                continue;
            }
            hasVariation = true;
            if (!qualifiers.matches("v\\d+")) {
                onlyVersion = false;
            }
        }

        if (hasVariation && !onlyVersion) {
            String message =
                    String.format(
                            "The resource `%1$s` is referenced from the manifest but varies across "
                                    + "configurations; manifest resources must be configuration "
                                    + "independent, except for the application title/icon or resources "
                                    + "that only vary by version",
                            value);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    private static boolean isApplicationTitleOrIcon(@NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
            int colon = name.indexOf(':');
            if (colon != -1) {
                name = name.substring(colon + 1);
            }
        }
        if (!name.equals(ATTR_LABEL)
                && !name.equals(ATTR_ICON)
                && !name.equals(ATTR_ROUND_ICON)) {
            return false;
        }
        String tag = attribute.getOwnerElement().getTagName();
        return tag.equals(TAG_APPLICATION);
    }
}