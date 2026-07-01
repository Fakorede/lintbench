package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.TAG_INCLUDE;
import static com.android.SdkConstants.TAG_MERGE;
import static com.android.resources.ResourceFolderType.LAYOUT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PositionXmlParser;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DuplicateIdDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIncludedIds",
            "Duplicate IDs across layouts combined with include tags",
            "If layouts are combined with include tags, then the id's need to be unique "
                    + "within any chain of included layouts, or Activity#findViewById() can "
                    + "return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            true,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE)
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == LAYOUT;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        checkFile(context);
    }

    private void checkFile(@NonNull XmlContext context) {
        Document document = context.document;
        if (document == null) {
            return;
        }
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        List<IdOccurrence> occurrences = new ArrayList<>();
        Set<File> chain = new HashSet<>();
        collectIdsFromElement(context, root, null, null, context.file, chain, occurrences);

        Map<String, List<IdOccurrence>> idMap = new HashMap<>();
        for (IdOccurrence occurrence : occurrences) {
            idMap.computeIfAbsent(occurrence.id, k -> new ArrayList<>()).add(occurrence);
        }

        for (Map.Entry<String, List<IdOccurrence>> entry : idMap.entrySet()) {
            List<IdOccurrence> list = entry.getValue();
            if (list.size() > 1) {
                reportDuplicate(context, entry.getKey(), list);
            }
        }
    }

    private void reportDuplicate(@NonNull XmlContext context, @NonNull String id,
            @NonNull List<IdOccurrence> occurrences) {
        IdOccurrence first = occurrences.get(0);
        String message = String.format(
                "Duplicate id `%1$s` across layouts combined with include tags; "
                        + "Activity#findViewById() can return an unexpected view",
                id);
        for (int i = 1; i < occurrences.size(); i++) {
            context.report(ISSUE, occurrences.get(i).location, message);
        }
    }

    private void collectIdsFromElement(@NonNull XmlContext context, @NonNull Element element,
            @Nullable String overrideId, @Nullable Location overrideLocation,
            @NonNull File sourceFile, @NonNull Set<File> chain,
            @NonNull List<IdOccurrence> occurrences) {
        if (TAG_INCLUDE.equals(element.getTagName())) {
            String layoutName = getLayoutName(element);
            if (layoutName != null) {
                File included = resolveLayout(context, layoutName);
                if (included != null) {
                    String includeOverrideId = getIdName(element);
                    Location includeOverrideLocation = null;
                    if (includeOverrideId != null) {
                        includeOverrideLocation = sourceFile.equals(context.file)
                                ? context.getLocation(element)
                                : Location.create(sourceFile);
                    }
                    collectIds(context, included, includeOverrideId, includeOverrideLocation,
                            chain, occurrences);
                    return;
                }
            }
        }

        String id = overrideId;
        Location location = overrideLocation;
        if (id == null) {
            id = getIdName(element);
            if (id != null) {
                location = sourceFile.equals(context.file)
                        ? context.getLocation(getIdAttr(element))
                        : Location.create(sourceFile);
            }
        }

        if (id != null && !TAG_MERGE.equals(element.getTagName())) {
            occurrences.add(new IdOccurrence(id,
                    location != null ? location : Location.create(sourceFile)));
        }

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectIdsFromElement(context, (Element) child, null, null, sourceFile, chain,
                        occurrences);
            }
            child = child.getNextSibling();
        }
    }

    private void collectIds(@NonNull XmlContext context, @NonNull File file,
            @Nullable String overrideId, @Nullable Location overrideLocation,
            @NonNull Set<File> chain, @NonNull List<IdOccurrence> occurrences) {
        if (!chain.add(file)) {
            return;
        }
        try {
            Document document = PositionXmlParser.parse(file);
            if (document != null) {
                Element root = document.getDocumentElement();
                if (root != null) {
                    collectIdsFromElement(context, root, overrideId, overrideLocation, file,
                            chain, occurrences);
                }
            }
        } catch (Exception e) {
            // Ignore parse errors in included layouts
        } finally {
            chain.remove(file);
        }
    }

    @Nullable
    private File resolveLayout(@NonNull XmlContext context, @NonNull String layoutName) {
        LintClient client = context.getClient();
        Project project = context.getProject();
        File file = client.findResource(project, LAYOUT.getName(), layoutName);
        if (file != null && file.isFile()) {
            return file;
        }
        List<File> resourceFolders = project.getResourceFolders();
        if (resourceFolders != null) {
            for (File resDir : resourceFolders) {
                File layoutDir = new File(resDir, LAYOUT.getName());
                File candidate = new File(layoutDir, layoutName + DOT_XML);
                if (candidate.isFile()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    @Nullable
    private static String getLayoutName(@NonNull Element element) {
        String value = element.getAttribute(ATTR_LAYOUT);
        if (value.isEmpty()) {
            value = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT);
        }
        return getResourceName(value);
    }

    @Nullable
    private static String getIdName(@NonNull Element element) {
        Attr attr = getIdAttr(element);
        return attr != null ? getResourceName(attr.getValue()) : null;
    }

    @Nullable
    private static Attr getIdAttr(@NonNull Element element) {
        Attr attr = element.getAttributeNode(ATTR_ID);
        if (attr == null) {
            attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
        }
        return attr;
    }

    @Nullable
    private static String getResourceName(@Nullable String value) {
        if (value == null || !value.startsWith(PREFIX_RESOURCE_REF)) {
            return null;
        }
        int slash = value.lastIndexOf('/');
        if (slash == -1) {
            return null;
        }
        String name = value.substring(slash + 1);
        int colon = name.indexOf(':');
        if (colon != -1) {
            name = name.substring(0, colon);
        }
        return name;
    }

    private static class IdOccurrence {
        @NonNull final String id;
        @NonNull final Location location;

        IdOccurrence(@NonNull String id, @NonNull Location location) {
            this.id = id;
            this.location = location;
        }
    }
}