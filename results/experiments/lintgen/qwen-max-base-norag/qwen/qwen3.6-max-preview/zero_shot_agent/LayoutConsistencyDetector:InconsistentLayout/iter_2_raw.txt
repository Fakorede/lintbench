package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Element;

import java.io.File;
import java.util.*;

import static com.android.tools.lint.detector.api.XmlScanner.ALL;

public class LayoutConsistencyDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
        "InconsistentLayout",
        "Inconsistent Layouts",
        "This check ensures that a layout resource which is defined in multiple " +
        "resource folders, specifies the same set of widgets.\n\n" +
        "This finds cases where you have accidentally forgotten to add " +
        "a widget to all variations of the layout, which could result " +
        "in a runtime crash for some resource configurations when a " +
        "`findViewById()` fails.\n\n" +
        "There **are** cases where this is intentional. For example, you " +
        "may have a dedicated large tablet layout which adds some extra " +
        "widgets that are not present in the phone version of the layout. " +
        "As long as the code accessing the layout resource is careful to " +
        "handle this properly, it is valid. In that case, you can suppress " +
        "this lint check for the given extra or missing views, or the whole layout.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(LayoutConsistencyDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final Map<String, List<LayoutVariant>> layouts = new HashMap<>();

    private static class LayoutVariant {
        final String folderName;
        final File file;
        final Set<String> ids = new HashSet<>();

        LayoutVariant(String folderName, File file) {
            this.folderName = folderName;
            this.file = file;
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id == null || id.isEmpty()) {
            return;
        }

        String idName = parseId(id);
        if (idName == null) {
            return;
        }

        String baseName = getBaseName(context.file);
        File parent = context.file.getParentFile();
        String folderName = parent != null ? parent.getName() : context.getResourceFolderType().getName();

        List<LayoutVariant> variants = layouts.computeIfAbsent(baseName, k -> new ArrayList<>());
        LayoutVariant variant = findOrCreateVariant(variants, folderName, context.file);
        variant.ids.add(idName);
    }

    private LayoutVariant findOrCreateVariant(List<LayoutVariant> variants, String folderName, File file) {
        for (LayoutVariant v : variants) {
            if (v.folderName.equals(folderName)) {
                return v;
            }
        }
        LayoutVariant newVariant = new LayoutVariant(folderName, file);
        variants.add(newVariant);
        return newVariant;
    }

    private static String parseId(String id) {
        if (id.startsWith("@+id/")) {
            return id.substring(5);
        } else if (id.startsWith("@id/")) {
            return id.substring(4);
        }
        return null;
    }

    private static String getBaseName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @Override
    public void afterCheckProject(Context context) {
        for (Map.Entry<String, List<LayoutVariant>> entry : layouts.entrySet()) {
            List<LayoutVariant> variants = entry.getValue();
            if (variants.size() < 2) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (LayoutVariant v : variants) {
                allIds.addAll(v.ids);
            }

            for (LayoutVariant v : variants) {
                Set<String> missing = new HashSet<>(allIds);
                missing.removeAll(v.ids);
                if (!missing.isEmpty()) {
                    List<String> sortedMissing = new ArrayList<>(missing);
                    Collections.sort(sortedMissing);
                    String missingList = String.join(", ", sortedMissing);
                    String message = String.format(
                        "The layout `%s` in folder `%s` is missing the following IDs found in other configurations: `%s`",
                        entry.getKey(), v.folderName, missingList
                    );
                    context.report(ISSUE, Location.create(v.file), message);
                }
            }
        }
    }
}