#!/usr/bin/env python3
"""Compare production Java tokens with reviewed upstream fingerprints.

The manifest was derived from the verbatim Arena import, not the adapted files.
Comments/whitespace do not count as code. Package/class/site-label replacements
were applied to the baseline before hashing. The sole behavioral exception is
removal of the upstream WebView UA-masking block; that exception is recorded in
the manifest. GitHubAllowlist is configuration, deliberately outside this check.
This does not prove Android/WebView correctness; the device tests cover that.
"""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
# Java production sources have no text blocks. Match strings before comments so
# URL literals containing // survive. Whitespace INSIDE strings is significant.
LEX = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/|\s+|[A-Za-z_$][\w$]*|[^\s]', re.S)


def tokens(source):
    return [m.group() for m in LEX.finditer(source)
            if not m.group().isspace() and not m.group().startswith(('//', '/*'))]


def fingerprint(source):
    return hashlib.sha256('\x1f'.join(tokens(source)).encode()).hexdigest()


def main():
    manifest = json.loads((ROOT / 'docs/upstream-core-fingerprints.json').read_text())
    expected = manifest['files']
    failed = []
    for name, row in expected.items():
        path = ROOT / name
        if not path.is_file() or fingerprint(path.read_text()) != row['adaptedCodeSha256']:
            failed.append(name)
    actual = {str(p.relative_to(ROOT)) for p in (ROOT / 'app/src/main/java').rglob('*.java')}
    configured = set(expected) | {manifest['configurationFile']}
    if actual != configured:
        failed += sorted(actual ^ configured)
    if failed:
        raise SystemExit('Unexpected production-core drift from Arena baseline:\n' + '\n'.join(failed))
    print(f"PASS: {len(expected)} production Java files match upstream executable tokens after "
          "recorded identifier/label substitutions and the single UA-honesty exception. "
          "Only GitHubAllowlist is site-policy configuration.")


if __name__ == '__main__':
    main()
