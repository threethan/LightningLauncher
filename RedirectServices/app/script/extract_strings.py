import os
import shutil
import subprocess
import sys

JAR_DIR = "apktool"
APK_DIR = "apk"
APK_NAME = "SystemUX"

STRING_XML_IN = "strings.xml"
STRING_XML_OUT = "target_name.xml"
VALUES_DIR = "values"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

CLEANUP_ONLY = "--clean-only" in sys.argv or "-c" in sys.argv
NO_DECOMPILE = "--no-decompile" in sys.argv or "-d" in sys.argv
NO_PULL = "--no-pull" in sys.argv or "-p" in sys.argv
HELP = "--help" in sys.argv or "-h" in sys.argv
HELP_STRING = """
Usage:
extract_strings.py [optional arguments]
Arguments:
--clean-only, -c: Clean output files only, don't generate new ones
--no-decompile, -d: Skip decompiling of APK using apktool
--no-pull, -p: Skip pulling of APK using adb
"""

XML_TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="target_name">{}</string>
    <string name="target_name_alt">{}</string>
</resources>
"""


def pull_apk():
    apk_dir = os.path.join(SCRIPT_DIR, APK_DIR)
    if not os.path.isdir(apk_dir):
        os.mkdir(apk_dir)

    print("Getting path via adb shell")
    apk_path: str = subprocess.check_output(['adb', 'shell', 'pm path com.oculus.systemux']).decode("utf-8")
    apk_path = apk_path.strip().removeprefix("package:/")
    print("Copying from", apk_path, "via adb pull")
    subprocess.run(['adb', 'pull', str(apk_path), apk_dir])


def extract_apk():
    apk_path = os.path.join(SCRIPT_DIR, APK_DIR, APK_NAME+".apk")
    apktool_dir = os.path.join(SCRIPT_DIR, JAR_DIR)
    if not os.path.isfile(apk_path):
        raise RuntimeError("Apk not found. Get systemux.apk from your quest via adb pull, place it at "
                           + apk_path)
    if not os.path.isdir(apktool_dir):
        os.mkdir(apktool_dir)
    for filename in os.listdir(apktool_dir):
        if filename.endswith(".jar"):
            jar_file = os.path.join(apktool_dir, filename)
            print("Running apktool...")
            subprocess.run(['java', '-jar', jar_file, 'd', '--no-src', '-f', apk_path])
            break
    else:
        raise RuntimeError("ApkTool jar not found. Must be placed in " + apk_path)


def generate_strings(target_string: str, target_string_alt: str, target_dir: str):
    count = 0
    default = ""

    res_dir = os.path.join(SCRIPT_DIR, APK_NAME, "res")
    out_dir = os.path.join(target_dir)

    if not os.path.isdir(res_dir):
        raise RuntimeError("Decompiled apk resources not found, make sure SystemUX apk has been decompiled")

    cleanup_old_outputs(out_dir)

    if CLEANUP_ONLY:
        print("Finished cleaning", out_dir)
        return

    untranslated_file = os.path.join(res_dir, VALUES_DIR, STRING_XML_IN)
    if not os.path.isfile(untranslated_file):
        raise RuntimeError("Untranslated strings.xml not found")

    target_key = get_key_matching_target_string(untranslated_file, target_string)
    if target_key == None:
        raise RuntimeError("Untranslated value matching", target_string, "not found")
    target_key_alt = get_key_matching_target_string(untranslated_file, target_string_alt)

    for value_folder in os.listdir(res_dir):
        if value_folder.startswith(VALUES_DIR):
            in_path = os.path.join(res_dir, value_folder, STRING_XML_IN)
            if os.path.isfile(in_path):
                value     = get_target_string_value(in_path, target_key)
                value_alt = get_target_string_value(in_path, target_key_alt) if target_key_alt else ""
                if value is not None:
                    out_path = in_path.replace(res_dir, out_dir).replace(STRING_XML_IN, STRING_XML_OUT)
                    out_path_dir = out_path.replace(STRING_XML_OUT, "")
                    if not os.path.isdir(out_path_dir):
                        os.makedirs(out_path_dir)

                    write_string_file(out_path, value, value_alt)
                    count += 1
    print("Wrote {} translations of '{}' from '{}'".format(count, target_string, target_key))


def cleanup_old_outputs(out_dir: str):
    out_dir = os.path.abspath(out_dir)
    # Cleanup
    if os.path.isdir(out_dir):
        for value_folder in os.listdir(out_dir):
            if os.path.isdir(os.path.join(out_dir, value_folder)) and value_folder.startswith(VALUES_DIR):
                res_file_list = os.listdir(os.path.join(out_dir, value_folder))
                for filename in res_file_list:
                    if filename == STRING_XML_OUT:
                        os.remove(os.path.join(out_dir, value_folder, filename))
                        if len(res_file_list) <= 1:
                            os.rmdir(os.path.join(out_dir, value_folder))


def get_target_string_value(path: str, key: str):
    import xml.etree.ElementTree as ElementTree
    tree = ElementTree.parse(path)

    root = tree.getroot()

    # Find the first occurrence of the tag and return its text
    for tag in root.findall('string'):
        if tag.get('name') == key:
            return tag.text

def get_key_matching_target_string(path: str, target_string: str):
    import xml.etree.ElementTree as ElementTree
    tree = ElementTree.parse(path)

    root = tree.getroot()

    # Find the last occurrence of the string and return its tag
    for tag in reversed(root.findall('string')):
        if tag.text == target_string:
            return tag.get('name')

def write_string_file(path: str, value: str, value_alt):
    with open(path, 'w', encoding='utf-8') as file:
        file.write(XML_TEMPLATE.format(value, value_alt))


def generate_all_strings():
    generate_strings("Horizon Feed", "Feed", "../src/feed/res")
    generate_strings("People", "", "../src/people/res")
    generate_strings("Meta Horizon Store", "Store", "../src/store/res")
    generate_strings("App Library", "Library", "../src/library/res")
    generate_strings("Library", "Navigator", "../src/navigator/res")


def run():
    if HELP:
        print(HELP_STRING)
        return

    print("Running accessibility event string generation...")
    try:
        if not NO_PULL:
            pull_apk()
        if not NO_DECOMPILE:
            extract_apk()
        generate_all_strings()
    except RuntimeError as e:
        print("An exception occurred:", e)
        print("Accessibility event string will not be updated, "
              "but may still work correctly if unchanged.")


run()
