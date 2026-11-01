#!/usr/bin/env python3
"""Print a Markdown coverage table from a JaCoCo XML report.

Used by the agent pipeline to post coverage on the PR, and by agents to find untested code:
    python3 scripts/coverage-summary.py [target/site/jacoco/jacoco.xml]
"""
import sys
import xml.etree.ElementTree as ET


def ratio(node, counter_type):
    for c in node.findall("counter"):
        if c.get("type") == counter_type:
            missed, covered = int(c.get("missed")), int(c.get("covered"))
            total = missed + covered
            return covered / total if total else 1.0, missed
    return 1.0, 0


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "target/site/jacoco/jacoco.xml"
    root = ET.parse(path).getroot()
    print("| Package | Line | Branch | Missed lines |")
    print("|---|---|---|---|")
    for pkg in sorted(root.findall("package"), key=lambda p: p.get("name")):
        line, missed = ratio(pkg, "LINE")
        branch, _ = ratio(pkg, "BRANCH")
        print(f"| `{pkg.get('name').replace('/', '.')}` | {line:.0%} | {branch:.0%} | {missed} |")
    line, missed = ratio(root, "LINE")
    branch, _ = ratio(root, "BRANCH")
    print(f"| **Total** | **{line:.1%}** | **{branch:.1%}** | **{missed}** |")


if __name__ == "__main__":
    main()
