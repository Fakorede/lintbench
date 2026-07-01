package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class IconDetector extends Detector {

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is really in the PNG format and not for example a GIF file named `.png`).",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE)
    );

    @Override
    public void run(Context context) {
        File file = context.file;
        if (file == null || !file.isFile()) {
            return;
        }

        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }
        String parentName = parent.getName();
        if (!parentName.startsWith("drawable") && !parentName.startsWith("mipmap")) {
            return;
        }

        String name = file.getName().toLowerCase(Locale.US);
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            ext = name.substring(dot + 1);
        }

        if (ext.isEmpty()) {
            return;
        }

        byte[] header = new byte[12];
        int read;
        try (InputStream is = new FileInputStream(file)) {
            read = is.read(header);
        } catch (IOException e) {
            return;
        }

        if (read < 2) {
            return;
        }

        boolean isPng = read >= 4 && header[0] == (byte) 0x89 && header[1] == (byte) 0x50 && header[2] == (byte) 0x4E && header[3] == (byte) 0x47;
        boolean isJpg = read >= 2 && header[0] == (byte) 0xFF && header[1] == (byte) 0xD8;
        boolean isGif = read >= 4 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8';
        boolean isWebp = read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
        boolean isBmp = read >= 2 && header[0] == 'B' && header[1] == 'M';

        String actualFormat = null;
        if (isPng) actualFormat = "PNG";
        else if (isJpg) actualFormat = "JPEG";
        else if (isGif) actualFormat = "GIF";
        else if (isWebp) actualFormat = "WebP";
        else if (isBmp) actualFormat = "BMP";

        if (actualFormat != null) {
            boolean mismatch = false;
            if (ext.equals("png") && !actualFormat.equals("PNG")) {
                mismatch = true;
            } else if ((ext.equals("jpg") || ext.equals("jpeg")) && !actualFormat.equals("JPEG")) {
                mismatch = true;
            } else if (ext.equals("gif") && !actualFormat.equals("GIF")) {
                mismatch = true;
            } else if (ext.equals("webp") && !actualFormat.equals("WebP")) {
                mismatch = true;
            } else if (ext.equals("bmp") && !actualFormat.equals("BMP")) {
                mismatch = true;
            }

            if (mismatch) {
                String message = String.format("File extension does not match actual format (%s)", actualFormat);
                context.report(ISSUE, Location.create(file), message);
            }
        }
    }
}