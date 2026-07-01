package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;

import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LayoutConsistencyDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "InconsistentLayout",
            "Inconsistent Layouts",
            "This check ensures that a layout resource which is defined in multiple resource "
                    + "folders specifies the same set of widgets. This finds cases where you have "
                    + "accidentally forgotten to add a widget to all variations of the layout, "
                    + "which could result in a runtime crash for some resource configurations "
                    + "when a `findViewById()` fails.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(LayoutConsistencyDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ID_PREFIX_PLUS = "@+id/";
    private static final String ID_PREFIX_REF = "@id/";

    private Project mProject;
    private Map<String, List<LayoutVariation>> mLayoutVariations;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mProject = context.getProject();
        mLayoutVariations = new HashMap<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Project project = context.getProject();
        if (project != mProject) {
            mProject = project;
            mLayoutVariations = new HashMap<>();
        }

        String layoutName = getLayoutName(context.file.getName());
        String folderName = context.file.getParentFile().getName();

        List<LayoutVariation> variations = mLayoutVariations.get(layoutName);
        if (variations == null) {
            variations = new ArrayList<>();
            mLayoutVariations.put(layoutName, variations);
        }

        LayoutVariation variation = null;
        for (LayoutVariation v : variations) {
            if (v.folder.equals(folderName)) {
                variation = v;
                break;
            }
        }
        if (variation == null) {
            variation = new LayoutVariation(folderName, context.file);
            variations.add(variation);
        }

        if (variation.rootLocation == null) {
            variation.rootLocation = context.getLocation(element);
        }

        String idValue = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (idValue != null && !idValue.isEmpty()) {
            String id = stripIdPrefix(idValue);
            if (!id.isEmpty()) {
                variation.ids.add(id);
                variation.idLocations.put(id, context.getLocation(element));
            }
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mLayoutVariations == null) {
            return;
        }

        for (Map.Entry<String, List<LayoutVariation>> entry : mLayoutVariations.entrySet()) {
            List<LayoutVariation> variations = entry.getValue();
            if (variations.size() < 2) {
                continue;
            }

            Set<String> union = new HashSet<>();
            for (LayoutVariation v : variations) {
                union.addAll(v.ids);
            }

            if (union.isEmpty()) {
                continue;
            }

            for (LayoutVariation v : variations) {
                Set<String> missing = new HashSet<>(union);
                missing.removeAll(v.ids);
                if (missing.isEmpty()) {
                    continue;
                }

                StringBuilder message = new StringBuilder();
                message.append("This layout is missing widgets that are present in other "
                        + "configurations: ");
                boolean first = true;
                for (String id : missing) {
                    if (!first) {
                        message.append(", ");
                    }
                    first = false;
                    message.append('@').append(id);

                    List<String> folders = new ArrayList<>();
                    for (LayoutVariation other : variations) {
                        if (other != v && other.ids.contains(id)) {
                            folders.add(other.folder);
                        }
                    }
                    if (!folders.isEmpty()) {
                        Collections.sort(folders);
                        message.append(" (in ").append(join(folders)).append(')');
                    }
                }

                context.report(ISSUE, v.rootLocation, message.toString());
            }
        }

        mLayoutVariations = null;
    }

    private static String getLayoutName(String fileName) {
        int dot = fileName.indexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }

    private static String stripIdPrefix(String idValue) {
        if (idValue.startsWith(ID_PREFIX_PLUS)) {
            return idValue.substring(ID_PREFIX_PLUS.length());
        }
        if (idValue.startsWith(ID_PREFIX_REF)) {
            return idValue.substring(ID_PREFIX_REF.length());
        }
        return idValue;
    }

    private static String join(List<String> items) {
        if (items.size() == 1) {
            return items.get(0);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                if (i == items.size() - 1) {
                    sb.append(" and ");
                } else {
                    sb.append(", ");
                }
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    private static class LayoutVariation {
        final String folder;
        final File file;
        final Set<String> ids = new HashSet<>();
        final Map<String, Location> idLocations = new HashMap<>();
        Location rootLocation;

        LayoutVariation(String folder, File file) {
            this.folder = folder;
            this.file = file;
        }
    }
}