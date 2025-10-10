package com.threethan.launcher.helper;

import android.content.Context;

import java.util.List;
import java.util.stream.Collectors;

public abstract class VariantHelper {
    /** Potential variant package names */
    private static final List<String> variants = List.of(
            "com.threethan.launcher",
            "com.threethan.launcher.metastore",
            "com.threethan.launcher.playstore");

    /** Get the package names of all variants which are present on the device,
     * excluding this one. */
    public static List<String> getInstalledVariantPackages(Context context) {
        return variants.stream().filter(pkg
                        -> (AppExt.doesPackageExist(context, pkg))
                        && !pkg.equals(context.getPackageName()))
                .collect(Collectors.toList());
    }

    /** Check if any variants are installed on the device, excluding this one. */
    public static boolean hasVariants(Context context) {
        return !getInstalledVariantPackages(context).isEmpty();
    }
}
