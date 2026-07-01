package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class ManifestResourceDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ManifestResource",
            "Manifest Resource References",
            "Elements in the manifest can reference resources, but those resources cannot " +
            "vary across configurations (except as a special case, by version, and except " +
            "for a few specific package attributes such as the application title and icon).",
            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final Set<String> ALLOWED_ATTRIBUTES;
    static {
        Set<String> set = new HashSet<>();
        set.add("label");
        set.add("icon");
        set.add("roundIcon");
        set.add("theme");
        set.add("banner");
        set.add("logo");
        set.add("description");
        set.add("sharedUserLabel");
        ALLOWED_ATTRIBUTES = Collections.unmodifiableSet(set);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attribute = (Attr) attributes.item(i);
            String value = attribute.getValue();
            if (value.startsWith("@") && !value.startsWith("@android:")) {
                String localName = attribute.getLocalName();
                if (ALLOWED_ATTRIBUTES.contains(localName)) {
                    continue;
                }

                int slash = value.indexOf('/');
                if (slash == -1) {
                    continue;
                }
                int colon = value.indexOf(':');
                String typeString;
                if (colon != -1 && colon < slash) {
                    continue;
                } else {
                    typeString = value.substring(1, slash);
                }
                String name = value.substring(slash + 1);

                ResourceType type = ResourceType.fromXmlValue(typeString);
                if (type == null) {
                    continue;
                }

                ResourceRepository repository = context.getClient().getResourceRepository(context.getProject(), true, false);
                if (repository == null) {
                    continue;
                }

                List<ResourceItem> items = repository.getResources(ResourceNamespace.RES_AUTO, type, name);
                for (ResourceItem item : items) {
                    FolderConfiguration config = item.getConfiguration();
                    if (!isAllowedConfig(config)) {
                        Location location = context.getValueLocation(attribute);
                        context.report(
                                ISSUE,
                                attribute,
                                location,
                                String.format(
                                        "The resource `%s` can vary by configurations other than API level",
                                        value
                                )
                        );
                        break;
                    }
                }
            }
        }
    }

    private static boolean isAllowedConfig(FolderConfiguration config) {
        int count = config.getQualifierCount();
        for (int i = 0; i < count; i++) {
            ResourceQualifier qualifier = config.getQualifier(i);
            if (qualifier != null && !(qualifier instanceof VersionQualifier)) {
                return false;
            }
        }
        return true;
    }
}