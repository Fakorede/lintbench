package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceUrl;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.List;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class ManifestResourceDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ManifestResource",
            "Manifest Resource References",
            "Elements in the manifest can reference resources, but those resources cannot "
                    + "vary across configurations (except as a special case, by version, and except "
                    + "for a few specific package attributes such as the application title and icon).",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Detector.XmlScanner.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            String value = attr.getValue();
            if (value.isEmpty() || value.charAt(0) != '@') {
                continue;
            }

            ResourceUrl url = ResourceUrl.parse(value);
            if (url == null || url.framework || url.theme) {
                continue;
            }

            ResourceType type = ResourceType.getEnum(url.type);
            if (type == null) {
                continue;
            }

            if (isApplicationIconOrLabel(element, attr)) {
                continue;
            }

            ResourceRepository repository = context.getProject().getResourceRepository();
            if (repository == null) {
                continue;
            }

            List<ResourceItem> items = repository.getResourceItem(type, url.name);
            if (items == null) {
                continue;
            }

            for (ResourceItem item : items) {
                FolderConfiguration config = item.getConfiguration();
                if (config == null) {
                    continue;
                }
                for (ResourceQualifier qualifier : config.getQualifiers()) {
                    if (qualifier != null
                            && qualifier.isValid()
                            && !(qualifier instanceof VersionQualifier)) {
                        context.report(
                                ISSUE,
                                attr,
                                context.getLocation(attr),
                                "Resources referenced from the manifest cannot vary across "
                                        + "configurations (except as a special case by version, and "
                                        + "except for a few specific package attributes such as the "
                                        + "application title and icon)");
                        return;
                    }
                }
            }
        }
    }

    private static boolean isApplicationIconOrLabel(@NonNull Element element, @NonNull Attr attr) {
        return SdkConstants.TAG_APPLICATION.equals(element.getTagName())
                && (SdkConstants.ATTR_ICON.equals(attr.getLocalName())
                        || SdkConstants.ATTR_LABEL.equals(attr.getLocalName()));
    }
}