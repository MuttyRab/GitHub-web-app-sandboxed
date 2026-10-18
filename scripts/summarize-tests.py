#!/usr/bin/env python3
"""Expose actual JUnit counts (including skips) through the GitHub Checks API."""
import glob
import sys
import xml.etree.ElementTree as ET


def escape(text):
    return text.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')


label, pattern = sys.argv[1:3]
total = failed = skipped = 0
for path in sorted(glob.glob(pattern, recursive=True)):
    root = ET.parse(path).getroot()
    for case in root.iter('testcase'):
        total += 1
        problem = case.find('failure')
        if problem is None:
            problem = case.find('error')
        if problem is not None:
            failed += 1
            if failed <= 8:
                title = case.get('classname', '?') + '.' + case.get('name', '?')
                message = ((problem.get('message') or '') + '\n' + (problem.text or ''))[:2500]
                print(f'::error title={escape(title)}::{escape(message)}')
        if case.find('skipped') is not None:
            skipped += 1
            print(f"::notice title=SKIPPED {escape(label)}::{escape(case.get('classname', '') + '.' + case.get('name', ''))}")
print(f'::notice title={escape(label)} results::{total} tests; {total-failed-skipped} passed; {failed} failed; {skipped} skipped')
if not total:
    print(f'::error title={escape(label)}::No test results produced; this is not a pass.')
    sys.exit(1)
if failed:
    sys.exit(1)
