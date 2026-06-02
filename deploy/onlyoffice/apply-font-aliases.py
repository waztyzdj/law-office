import argparse
from pathlib import Path
import re


GENERATOR = Path("/usr/bin/documentserver-generate-allfonts.sh")
GENERATOR_HOOK = "python3 /usr/local/bin/law-office-apply-font-aliases.py || true"
GENERATOR_MARKER = '  --use-system-user-fonts="false"'

ALL_FONTS_FILES = [
    Path("/var/www/onlyoffice/documentserver/sdkjs/common/AllFonts.js"),
    Path("/var/www/onlyoffice/documentserver/server/FileConverter/bin/AllFonts.js"),
]

ALIASES = {
    "FangSong": ["仿宋", "仿宋_GB2312", "FangSong_GB2312"],
    "KaiTi": ["楷体", "楷体_GB2312", "KaiTi_GB2312"],
    "SimHei": ["黑体", "黑体_GB2312", "SimHei_GB2312"],
    "SimSun": ["宋体", "宋体_GB2312", "SimSun_GB2312"],
    "NSimSun": ["新宋体", "新宋体_GB2312", "NSimSun_GB2312"],
    "Microsoft YaHei": ["微软雅黑"],
    "Microsoft YaHei Light": ["微软雅黑 Light"],
    "DengXian": ["等线"],
    "DengXian Light": ["等线 Light"],
}


def read_font_infos(text: str) -> dict[str, str]:
    infos_start = text.index('window["__fonts_infos"] = [')
    infos_end = text.index("\n];", infos_start)
    infos_text = text[infos_start:infos_end]
    result: dict[str, str] = {}
    for match in re.finditer(r'^\["((?:[^"\\]|\\.)+)",(.+?)\],$', infos_text, re.MULTILINE):
        result[match.group(1)] = match.group(2)
    return result


def remove_existing_aliases(text: str) -> str:
    for alias in {alias for aliases in ALIASES.values() for alias in aliases}:
        text = re.sub(
            rf'^\["{re.escape(alias)}",.+?\],\n?',
            "",
            text,
            flags=re.MULTILINE,
        )
    return text


def apply_aliases(path: Path) -> None:
    text = path.read_text(encoding="utf-8-sig")
    text = remove_existing_aliases(text)
    infos = read_font_infos(text)
    additions: list[str] = []

    for source_name, aliases in ALIASES.items():
        source_tail = infos.get(source_name)
        if not source_tail:
            print(f"Skip {source_name}: source font is not present in {path}")
            continue
        for alias in aliases:
            additions.append(f'["{alias}",{source_tail}],')

    if not additions:
        print(f"No font aliases changed in {path}")
        return

    infos_start = text.index('window["__fonts_infos"] = [')
    infos_end = text.index("\n];", infos_start)
    prefix = text[:infos_end].rstrip()
    separator = "\n" if prefix.endswith("[") or prefix.endswith(",") else ",\n"
    insertion = separator + "\n".join(additions)
    text = text[:infos_end] + insertion + text[infos_end:]
    path.write_text(text, encoding="utf-8-sig")
    print(f"Added {len(additions)} font aliases to {path}")


def patch_generator() -> None:
    text = GENERATOR.read_text(encoding="utf-8")
    if GENERATOR_HOOK in text:
        print(f"{GENERATOR} already contains law-office font alias hook")
        return

    marker_index = text.index(GENERATOR_MARKER)
    line_end = text.index("\n", marker_index)
    text = text[: line_end + 1] + "\n" + GENERATOR_HOOK + "\n" + text[line_end + 1 :]
    GENERATOR.write_text(text, encoding="utf-8")
    print(f"Added law-office font alias hook to {GENERATOR}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--patch-generator",
        action="store_true",
        help="Patch ONLYOFFICE font generation to reapply aliases after regeneration.",
    )
    args = parser.parse_args()

    if args.patch_generator:
        patch_generator()
        return

    for all_fonts_file in ALL_FONTS_FILES:
        apply_aliases(all_fonts_file)


if __name__ == "__main__":
    main()
