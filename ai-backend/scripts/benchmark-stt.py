#!/usr/bin/env python3
"""Evaluate /api/transcribe on fixed public human recordings and controlled variants.

Requires Python 3, numpy and ffmpeg. Audio is cached outside the repository.
No Android baseline is invented: optionally supply paired, actually measured baseline results.
"""
import argparse
import base64
import concurrent.futures
import hashlib
import io
import json
import math
import random
import re
import subprocess
import time
import unicodedata
import urllib.parse
import urllib.request
import wave
from pathlib import Path

SEED = 20261001
DATASET = "PolyAI/minds14"
REVISION = "40ce77cb32a384e4d50a568e1ec39ac804019d33"


def get_json(url):
    with urllib.request.urlopen(url, timeout=45) as response:
        return json.load(response)


def words(text):
    return re.findall(r"\w+(?:['’]\w+)*", unicodedata.normalize("NFKC", text).lower())


def characters(text):
    return [c for c in unicodedata.normalize("NFKC", text).lower()
            if not c.isspace() and not unicodedata.category(c).startswith("P")]


def edit_count(reference, hypothesis):
    previous = list(range(len(hypothesis) + 1))
    for i, ref in enumerate(reference, 1):
        current = [i]
        for j, hyp in enumerate(hypothesis, 1):
            current.append(min(previous[j] + 1, current[-1] + 1,
                               previous[j - 1] + (ref != hyp)))
        previous = current
    return previous[-1]


def wilson(success, total):
    if not total:
        return None
    z = 1.96
    p = success / total
    divisor = 1 + z * z / total
    center = (p + z * z / (2 * total)) / divisor
    radius = z * math.sqrt(p * (1 - p) / total + z * z / (4 * total * total)) / divisor
    return [round(max(0, center - radius) * 100, 2), round(min(1, center + radius) * 100, 2)]


def make_wav(pcm):
    output = io.BytesIO()
    with wave.open(output, "wb") as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(16000)
        audio.writeframes(pcm)
    return output.getvalue()


def to_pcm(path):
    return subprocess.check_output(["ffmpeg", "-v", "error", "-i", str(path),
                                    "-f", "s16le", "-ac", "1", "-ar", "16000", "-"])


def rows_for(config):
    def page(offset):
        query = urllib.parse.urlencode(dict(dataset=DATASET, config=config, split="train", offset=offset, length=100))
        return get_json("https://datasets-server.huggingface.co/rows?" + query)
    first = page(0)
    pages = [first]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
        pages += list(executor.map(page, range(100, first["num_rows_total"], 100)))
    return [entry for data in pages for entry in data["rows"]]


def select_human(config, per_intent, cache, seed=SEED, excluded_ids=None):
    metadata = get_json("https://huggingface.co/api/datasets/" + DATASET)
    if metadata.get("sha") != REVISION:
        raise ValueError("Dataset revision changed; use archived audio with matching SHA256")
    rows = rows_for(config)
    groups = {}
    for row in rows:
        groups.setdefault(row["row"]["intent_class"], []).append(row)
    rng = random.Random(seed + (0 if config == "de-DE" else 1))
    selected, excluded = [], []
    for intent, group in sorted(groups.items()):
        rng.shuffle(group)
        taken = 0
        for entry in group:
            row, index = entry["row"], entry["row_idx"]
            case_id = f"minds14-{config}-{index}"
            if case_id in (excluded_ids or set()):
                continue
            source = cache / (case_id + "-source.wav")
            if not source.exists():
                with urllib.request.urlopen(row["audio"][0]["src"], timeout=45) as response:
                    source.write_bytes(response.read())
            pcm = to_pcm(source)
            seconds = len(pcm) / 32000
            if not row["transcription"].strip() or not (0.25 <= seconds <= 30):
                excluded.append({"caseId": case_id, "reason": "duration_or_empty_reference", "seconds": seconds})
                continue
            path = cache / (case_id + "-clean.wav")
            path.write_bytes(make_wav(pcm))
            selected.append({"caseId": case_id + "-clean", "sourceId": case_id,
                             "datasetPath": row["path"], "datasetRow": index, "intent": intent,
                             "language": config[:2], "kind": "human_clean", "reference": row["transcription"],
                             "seconds": seconds, "audioFile": str(path)})
            taken += 1
            if taken == per_intent:
                break
        if taken != per_intent:
            raise RuntimeError(f"Not enough eligible audio for {config}, intent {intent}")
    return selected, excluded


def add_noise(case, cache):
    import numpy as np
    with wave.open(case["audioFile"], "rb") as audio:
        signal = np.frombuffer(audio.readframes(audio.getnframes()), dtype="<i2").astype(np.float64)
    # 10 dB SNR over the entire clip (including its original pauses), deterministic per source.
    seed = int(hashlib.sha256(case["sourceId"].encode()).hexdigest()[:8], 16)
    noise = np.random.default_rng(seed).normal(0, 1, len(signal))
    signal_rms = np.sqrt(np.mean(signal * signal))
    noise *= signal_rms / (10 ** (10 / 20)) / np.sqrt(np.mean(noise * noise))
    mixed = np.clip(np.rint(signal + noise), -32768, 32767).astype("<i2")
    path = cache / (case["sourceId"] + "-noise10db.wav")
    path.write_bytes(make_wav(mixed.tobytes()))
    return {**case, "caseId": case["sourceId"] + "-noise10db", "kind": "human_noise10db", "audioFile": str(path)}


def evaluate(case, base_url):
    audio = Path(case["audioFile"]).read_bytes()
    body = json.dumps({"audioBase64": base64.b64encode(audio).decode()}).encode()
    started = time.monotonic()
    row = {**case, "audioSha256": hashlib.sha256(audio).hexdigest()}
    try:
        request = urllib.request.Request(base_url.rstrip("/") + "/api/transcribe", data=body,
                                         headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=65) as response:
            result = json.load(response)
        row.update(ok=True, text=result["text"], languages=result.get("languages", []),
                   audioLanguages=result.get("audioLanguages"), languageSource=result.get("languageSource"),
                   model=result.get("model"), serverMs=result.get("elapsedMs"))
    except urllib.error.HTTPError as error:
        row.update(ok=False, text="", languages=[], error=f"HTTP {error.code}: " + error.read().decode()[:500])
    except Exception as error:
        row.update(ok=False, text="", languages=[], error=str(error))
    row["clientMs"] = round((time.monotonic() - started) * 1000)
    return row


def summarize(rows):
    groups = {}
    for row in rows:
        key = row["language"] + "/" + row["kind"]
        metric = groups.setdefault(key, dict(count=0, succeeded=0, languageCorrect=0, exactNormalized=0,
                                             wordErrors=0, referenceWords=0, charErrors=0, referenceChars=0, latency=[]))
        metric["count"] += 1
        metric["succeeded"] += bool(row.get("ok"))
        # A mixed-language label is not counted as a correct single-language decision.
        metric["languageCorrect"] += row.get("languages") == [row["language"]]
        ref, hyp = words(row["reference"]), words(row.get("text", ""))
        rc, hc = characters(row["reference"]), characters(row.get("text", ""))
        row["wordErrors"], row["referenceWords"] = edit_count(ref, hyp), len(ref)
        row["charErrors"], row["referenceChars"] = edit_count(rc, hc), len(rc)
        metric["wordErrors"] += row["wordErrors"]
        metric["referenceWords"] += len(ref)
        metric["charErrors"] += row["charErrors"]
        metric["referenceChars"] += len(rc)
        metric["exactNormalized"] += rc == hc
        if row.get("ok"):
            metric["latency"].append(row["clientMs"])
    for metric in groups.values():
        n = metric["count"]
        metric["languageAccuracyPct"] = round(metric["languageCorrect"] / n * 100, 2)
        metric["languageAccuracyWilson95Pct"] = wilson(metric["languageCorrect"], n)
        metric["wordErrorRatePct"] = round(metric["wordErrors"] / metric["referenceWords"] * 100, 2)
        metric["charErrorRatePct"] = round(metric["charErrors"] / metric["referenceChars"] * 100, 2)
        values = sorted(metric.pop("latency"))
        metric["clientMedianMs"] = round((values[(len(values)-1)//2] + values[len(values)//2]) / 2) if values else None
    return groups


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="https://ai-assistant-handmade.vercel.app")
    parser.add_argument("--cache", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--fixtures", type=Path, help="Additional locally generated case manifests")
    parser.add_argument("--cases-manifest", type=Path, help="Replay these exact cases/audio instead of selecting new cases")
    parser.add_argument("--exclude-manifest", type=Path, help="Exclude source IDs already used for development")
    parser.add_argument("--seed", type=int, default=SEED)
    parser.add_argument("--no-noise", action="store_true")
    parser.add_argument("--baseline-results", type=Path, help="Actual old recognizer output for identical case IDs/audio SHA256")
    args = parser.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)
    if args.cases_manifest:
        manifest = json.loads(args.cases_manifest.read_text())
        cases = manifest["cases"]
    else:
        excluded_ids = {case["sourceId"] for case in json.loads(args.exclude_manifest.read_text())["cases"]} if args.exclude_manifest else set()
        de, excluded_de = select_human("de-DE", 3, args.cache, args.seed, excluded_ids)
        en, excluded_en = select_human("en-US", 1, args.cache, args.seed, excluded_ids)
        seen, noisy = set(), []
        for case in de:
            if not args.no_noise and case["intent"] not in seen:
                noisy.append(add_noise(case, args.cache))
                seen.add(case["intent"])
        cases = de + noisy + en
        if args.fixtures:
            cases += json.loads(args.fixtures.read_text())
        manifest = {"seed": args.seed, "dataset": DATASET, "datasetRevision": REVISION,
                    "datasetLicense": "CC-BY-4.0", "source": "https://huggingface.co/datasets/PolyAI/minds14",
                    "cases": cases, "excluded": excluded_de + excluded_en}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.with_suffix(".manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2))
    print(json.dumps({"prepared": len(cases), "excluded": len(manifest["excluded"])}), flush=True)
    rows = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
        for row in executor.map(lambda case: evaluate(case, args.base_url), cases):
            rows.append(row)
            print(json.dumps({"case": row["caseId"], "ok": row["ok"], "languages": row["languages"]}), flush=True)
            args.output.write_text(json.dumps({"partial": True, "rows": rows}, ensure_ascii=False, indent=2))
    summary = summarize(rows)
    baseline = None
    if args.baseline_results:
        measured = json.loads(args.baseline_results.read_text())
        old = {row["caseId"]: row for row in measured["rows"]}
        if not measured.get("method") or not measured.get("device") or not measured.get("provider"):
            raise ValueError("A baseline needs actual method/device/provider metadata")
        if set(old) != {row["caseId"] for row in rows}:
            raise ValueError("Baseline must contain all the same cases, including failures")
        baseline_rows = []
        for row in rows:
            previous = old[row["caseId"]]
            if previous.get("audioSha256") != row["audioSha256"]:
                raise ValueError("Baseline audio differs; paired improvement cannot be calculated")
            baseline_rows.append({**row, **previous})
        baseline = {"method": measured["method"], "device": measured["device"], "provider": measured["provider"],
                    "summary": summarize(baseline_rows)}
    output = {"partial": False, "measuredAtUTC": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
              "endpoint": args.base_url + "/api/transcribe", "manifest": manifest,
              "scope": "Cloud transcription only; no Galaxy microphone/Android provider/VAD/TTS measurement",
              "normalization": "NFKC, lowercase, punctuation ignored; number representations not canonicalized",
              "baseline": baseline, "summary": summary, "rows": rows}
    args.output.write_text(json.dumps(output, ensure_ascii=False, indent=2))
    print(json.dumps({"summary": summary, "baseline": baseline}, ensure_ascii=False, indent=2), flush=True)


if __name__ == "__main__":
    main()
