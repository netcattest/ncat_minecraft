import ast
import io
import os
import sys
import tokenize

KEEP = ("copyright", "spdx", "licen")


def keeps(text):
    lowered = text.lower()
    return any(word in lowered for word in KEEP)


def strip_java(source):
    lines = source.split("\n")
    kept = [[] for _ in lines]
    row = col = 0
    state = "code"
    buffer = []
    buffer_rows = []
    changed = False

    def flush(keep):
        nonlocal buffer, buffer_rows
        if keep:
            for index, part in zip(buffer_rows, buffer):
                kept[index].append(part)
        buffer = []
        buffer_rows = []

    while row < len(lines):
        line = lines[row]
        if col >= len(line):
            if state == "block":
                buffer.append("")
                buffer_rows.append(row)
            row += 1
            col = 0
            continue
        rest = line[col:]
        if state == "code":
            nxt = min((i for i in (rest.find('"'), rest.find("'"), rest.find("//"), rest.find("/*"))
                       if i >= 0), default=-1)
            if nxt < 0:
                kept[row].append(rest)
                col = len(line)
                continue
            kept[row].append(rest[:nxt])
            col += nxt
            token = line[col:col + 2]
            if token == "//":
                changed = True
                col = len(line)
            elif token == "/*":
                changed = True
                state = "block"
                buffer = [line[col:col + 2]]
                buffer_rows = [row]
                col += 2
            elif line.startswith('"""', col):
                state = "text"
                kept[row].append('"""')
                col += 3
            elif line[col] == '"':
                state = "string"
                kept[row].append('"')
                col += 1
            else:
                state = "char"
                kept[row].append("'")
                col += 1
        elif state in ("string", "char"):
            quote = '"' if state == "string" else "'"
            index = col
            while index < len(line):
                if line[index] == "\\":
                    index += 2
                    continue
                if line[index] == quote:
                    break
                index += 1
            index = min(index, len(line))
            end = min(index + 1, len(line))
            kept[row].append(line[col:end])
            col = end
            if index < len(line):
                state = "code"
        elif state == "text":
            index = line.find('"""', col)
            if index < 0:
                kept[row].append(line[col:])
                col = len(line)
            else:
                kept[row].append(line[col:index + 3])
                col = index + 3
                state = "code"
        elif state == "block":
            index = line.find("*/", col)
            if index < 0:
                buffer.append(line[col:])
                buffer_rows.append(row)
                col = len(line)
            else:
                buffer.append(line[col:index + 2])
                buffer_rows.append(row)
                col = index + 2
                flush(keeps("".join(buffer)))
                state = "code"
    if state == "block":
        flush(True)

    out = []
    for index, parts in enumerate(kept):
        text = "".join(parts)
        original = lines[index]
        if not text.strip() and original.strip():
            continue
        out.append(text.rstrip() if text.strip() else text)
    return "\n".join(out), changed


def strip_python(source):
    try:
        tree = ast.parse(source)
    except SyntaxError:
        return source, False
    lines = source.split("\n")
    drop = set()
    inserts = {}
    for node in ast.walk(tree):
        if not isinstance(node, (ast.Module, ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)):
            continue
        body = getattr(node, "body", [])
        if not body:
            continue
        first = body[0]
        if not (isinstance(first, ast.Expr) and isinstance(first.value, ast.Constant)
                and isinstance(first.value.value, str)):
            continue
        for row in range(first.lineno - 1, first.end_lineno):
            drop.add(row)
        if len(body) == 1 and not isinstance(node, ast.Module):
            indent = len(lines[first.lineno - 1]) - len(lines[first.lineno - 1].lstrip())
            inserts[first.lineno - 1] = " " * indent + "pass"

    comment_rows = {}
    try:
        for token in tokenize.generate_tokens(io.StringIO(source).readline):
            if token.type == tokenize.COMMENT:
                comment_rows.setdefault(token.start[0] - 1, []).append(token.start[1])
    except tokenize.TokenError:
        pass

    out = []
    changed = bool(drop or comment_rows)
    for index, line in enumerate(lines):
        if index in drop:
            if index in inserts:
                out.append(inserts[index])
            continue
        if index in comment_rows:
            cut = min(comment_rows[index])
            head = line[:cut].rstrip()
            if not head:
                continue
            out.append(head)
            continue
        out.append(line)
    text = "\n".join(out)
    try:
        ast.parse(text)
    except SyntaxError as error:
        print("  ! python parse broke, kept original:", error, file=sys.stderr)
        return source, False
    return text, changed


def walk(root, suffix):
    for base, _, files in os.walk(root):
        for name in sorted(files):
            if name.endswith(suffix):
                yield os.path.join(base, name)


def main():
    touched = 0
    for path in walk("src/main/java", ".java"):
        source = io.open(path, encoding="utf-8").read()
        text, changed = strip_java(source)
        if changed and text != source:
            io.open(path, "w", encoding="utf-8", newline="\n").write(text)
            touched += 1
    print("java files cleaned:", touched)

    touched = 0
    for path in sorted(walk("tools", ".py")):
        source = io.open(path, encoding="utf-8").read()
        text, changed = strip_python(source)
        if changed and text != source:
            io.open(path, "w", encoding="utf-8", newline="\n").write(text)
            touched += 1
    print("python files cleaned:", touched)


if __name__ == "__main__":
    main()
