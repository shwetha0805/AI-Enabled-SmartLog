
import argparse
import csv
import json
import os
import urllib.request
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent

SOURCE_FIELDS = [
    "@timestamp",
    "event.code",
    "event.action",
    "event.provider",
    "event.outcome",
    "winlog.channel",
    "log.level"
]


def get_nested(data, path):
    for part in path.split("."):
        if not isinstance(data, dict):
            return None
        data = data.get(part)
    return data


def request_json(url, payload=None):
    if payload is None:
        request = urllib.request.Request(url)
    else:
        request = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST"
        )

    with urllib.request.urlopen(request, timeout=120) as response:
        return json.loads(response.read().decode("utf-8"))


def main():
    parser = argparse.ArgumentParser(
        description="Extract Windows event documents from Elasticsearch."
    )

    parser.add_argument(
        "--url",
        default=os.getenv("SMARTLOG_ES_URL", "http://localhost:9200"),
        help="Elasticsearch base URL."
    )

    parser.add_argument(
        "--index",
        default="smartlog-windows",
        help="Elasticsearch index name."
    )

    parser.add_argument(
        "--output",
        default=str(BASE_DIR / "data" / "smartlog_training_data.csv"),
        help="Output CSV file path."
    )

    args = parser.parse_args()

    base_url = args.url.rstrip("/")
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    payload = {
        "size": 1000,
        "sort": ["_doc"],
        "_source": SOURCE_FIELDS,
        "query": {"match_all": {}}
    }

    result = request_json(
        f"{base_url}/{args.index}/_search?scroll=2m",
        payload
    )

    scroll_id = result.get("_scroll_id")

    total_info = result["hits"]["total"]
    total = (
        total_info["value"]
        if isinstance(total_info, dict)
        else total_info
    )

    print("Elasticsearch URL:", base_url)
    print("Index:", args.index)
    print("Documents reported at extraction start:", total)

    count = 0

    try:
        with output_path.open(
            "w", newline="", encoding="utf-8"
        ) as file:
            writer = csv.DictWriter(
                file,
                fieldnames=[
                    "document_id",
                    "timestamp",
                    "event_code",
                    "event_action",
                    "event_provider",
                    "channel",
                    "log_level",
                    "outcome_audit"
                ]
            )

            writer.writeheader()

            while True:
                hits = result["hits"]["hits"]

                if not hits:
                    break

                for hit in hits:
                    source = hit.get("_source", {})

                    writer.writerow({
                        "document_id": hit.get("_id", ""),
                        "timestamp": source.get("@timestamp", ""),
                        "event_code": get_nested(
                            source, "event.code"
                        ) or "UNKNOWN",
                        "event_action": get_nested(
                            source, "event.action"
                        ) or "UNKNOWN",
                        "event_provider": get_nested(
                            source, "event.provider"
                        ) or "UNKNOWN",
                        "channel": get_nested(
                            source, "winlog.channel"
                        ) or "UNKNOWN",
                        "log_level": get_nested(
                            source, "log.level"
                        ) or "UNKNOWN",
                        "outcome_audit": get_nested(
                            source, "event.outcome"
                        ) or "UNKNOWN"
                    })

                count += len(hits)
                print(f"Extracted: {count}/{total}")

                result = request_json(
                    f"{base_url}/_search/scroll",
                    {
                        "scroll": "2m",
                        "scroll_id": scroll_id
                    }
                )

                scroll_id = result.get("_scroll_id", scroll_id)

    finally:
        if scroll_id:
            try:
                request_json(
                    f"{base_url}/_search/scroll",
                    {"scroll_id": [scroll_id]}
                )
            except Exception:
                pass

    print("\n===== EXTRACTION COMPLETE =====")
    print("Documents extracted:", count)
    print("CSV saved to:", output_path)


if __name__ == "__main__":
    main()
