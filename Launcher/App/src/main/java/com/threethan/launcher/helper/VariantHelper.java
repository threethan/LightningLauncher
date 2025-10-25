package com.threethan.launcher.helper;

import android.content.Context;

import com.threethan.launcher.R;

import java.util.List;
import java.util.stream.Collectors;

public abstract class VariantHelper {
    public static final String VARIANT_SIDELOAD = "com.threethan.launcher";
    public static final String VARIANT_METASTORE = "com.threethan.launcher.metastore";
    public static final String VARIANT_PLAYSTORE = "com.threethan.launcher.playstore";
    public static final String SOURCE_EXTRA = "source_package";

    /** Potential variant package names */
    private static final List<String> variants = List.of(
            VARIANT_SIDELOAD,
            VARIANT_METASTORE,
            VARIANT_PLAYSTORE);



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

    public static boolean isKnownVariant(String packageName) {
        return variants.contains(packageName);
    }

    public static int getNameResId(String packageName) {
        return switch (packageName) {
            case VARIANT_SIDELOAD -> R.string.variant_sideload;
            case VARIANT_METASTORE -> R.string.variant_metastore;
            case VARIANT_PLAYSTORE -> R.string.variant_playstore;
            default -> R.string.app_name;
        };
    }

    public static int getDetailsResId(String packageName) {
        return switch (packageName) {
            case VARIANT_SIDELOAD -> R.string.variant_sideload_details;
            case VARIANT_METASTORE -> R.string.variant_metastore_details;
            case VARIANT_PLAYSTORE -> R.string.variant_playstore_details;
            default -> R.string.app_name;
        };
    }

    public static int getIconResId(String packageName) {
        return switch (packageName) {
            case VARIANT_SIDELOAD -> R.drawable.ic_icon_grey;
            case VARIANT_METASTORE -> R.drawable.ic_icon_blue;
            case VARIANT_PLAYSTORE -> R.drawable.ic_icon_orange;
            default -> R.mipmap.app_icon;
        };
    }
}
