#!/usr/bin/env python3
"""Compare production Java tokens with reviewed upstream fingerprints.

The manifest was derived from the verbatim Arena import, not the adapted files.
Comments/whitespace do not count as code. Package/class/site-label replacements
were applied to the baseline before hashing. GitHubAllowlist is configuration,
deliberately outside this check.

Two behavioural exceptions are recorded, and both must stay declared:
  * removal of the upstream WebView UA-masking block; and
  * user-facing refusal text in MainActivity (onLoadError / onDownloadBlocked),
    which now names the refused host instead of blaming github.com, and shows a
    refused download on the overlay instead of only a toast. No decision, host,
    permission or load path changes.

A file may diverge from the imported baseline only through a
`reviewedException` row in docs/upstream-core-fingerprints.json. Such a row is
itself verified: it must carry a rationale, the hash of the reviewed code, the
tokens the change added (still present) and the tokens it replaced (still gone).
Anything else is drift and fails. This does not prove Android/WebView
correctness; the device tests cover that.
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


def check_exception(name, current_tokens, current_hash, exception):
    """Return None when the recorded exception is legitimately in force, else a failure note."""
    rationale = exception.get('rationale')
    if not rationale or not str(rationale).strip():
        return 'recorded exception without a rationale'
    if current_hash != exception.get('adaptedCodeSha256'):
        return 'changed outside the recorded exception'
    for token in exception.get('requiredTokens') or []:
        if token not in current_tokens:
            return 'recorded change is missing: ' + token
    for token in exception.get('forbiddenTokens') or []:
        if token in current_tokens:
            return 'a replaced construct is back: ' + token
    return None


def main():
    manifest = json.loads((ROOT / 'docs/upstream-core-fingerprints.json').read_text())
    expected = manifest['files']
    failed = []
    recorded = []
    for name, row in expected.items():
        path = ROOT / name
        if not path.is_file():
            failed.append(name + ' (missing)')
            continue
        current_tokens = tokens(path.read_text())
        current_hash = hashlib.sha256('\x1f'.join(current_tokens).encode()).hexdigest()
        exception = row.get('reviewedException')
        if current_hash == row['adaptedCodeSha256']:
            if exception:
                failed.append(name + ' (stale reviewedException: the file matches the imported baseline)')
            continue
        if not exception:
            failed.append(name)
            continue
        problem = check_exception(name, current_tokens, current_hash, exception)
        if problem:
            failed.append(f'{name} ({problem})')
        else:
            recorded.append((name, exception.get('summary', '')))
    actual = {str(p.relative_to(ROOT)) for p in (ROOT / 'app/src/main/java').rglob('*.java')}
    configured = set(expected) | {manifest['configurationFile']}
    if actual != configured:
        failed += sorted(actual ^ configured)
    if failed:
        raise SystemExit('Unexpected production-core drift from Arena baseline:\n' + '\n'.join(failed))
    print(f"PASS: {len(expected)} production Java files match upstream executable tokens after "
          "recorded identifier/label substitutions, the UA-honesty exception and "
          f"{len(recorded)} documented refusal-message exception(s). "
          "Only GitHubAllowlist is site-policy configuration.")
    for name, summary in recorded:
        print(f"  reviewed exception: {name} - {summary}")


if __name__ == '__main__':
    main()
