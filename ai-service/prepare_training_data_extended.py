
import argparse
import json
import csv
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
    "winlog.record_id",
    "winlog.event_data.ProcessName",
    "winlog.event_data.ObjectType",
    "winlog.event_data.ObjectName",
    "winlog.event_data.SubjectUserSid",
    "host.name",
    "log.level"
]


def extract_value(source, path):
    value = source

    for key in path.split("."):
        if not isinstance(value, dict):
            return ""

        value = value.get(key, "")

    if value is None:
        return ""

    if isinstance(value, (dict, list)):
        return json.dumps(value, ensure_ascii=False)

    return value


def request_json(url, payload, method="POST"):
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method=method
    )

    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def main():
    parser = argparse.ArgumentParser(
        description="Extract extended Windows event data from Elasticsearch."
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
        default=str(
            BASE_DIR / "data" / "smartlog_training_data_extended.csv"
        ),
        help="Output CSV file path."
    )

    args = parser.parse_args()

    base_url = args.url.rstrip("/")
    search_url = f"{base_url}/{args.index}/_search"
    scroll_url = f"{base_url}/_search/scroll"

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    rows = []
    scroll_id = None
    total = None

    try:
        payload = {
            "size": 1000,
            "track_total_hits": True,
            "_source": SOURCE_FIELDS,
            "query": {"match_all": {}},
            "sort": ["_doc"]
        }

        result = request_json(
            search_url + "?scroll=2m",
            payload
        )

        scroll_id = result.get("_scroll_id")
        total = result["hits"]["total"]["value"]

        print("Elasticsearch URL:", base_url)
        print("Index:", args.index)
        print("Documents reported at extraction start:", total)

        while True:
            hits = result["hits"]["hits"]

            if not hits:
                break

            for hit in hits:
                source = hit.get("_source", {})

                row = {
                    "document_id": hit.get("_id", "")
                }

                for field in SOURCE_FIELDS:
                    column = field.replace(".", "_")
                    row[column] = extract_value(source, field)

                rows.append(row)

            print(f"Extracted: {len(rows)} / {total}")

            if not scroll_id:
                break

            scroll_payload = {
                "scroll": "2m",
                "scroll_id": scroll_id
            }

            result = request_json(
                scroll_url,
                scroll_payload
            )

            scroll_id = result.get("_scroll_id", scroll_id)

        columns = ["document_id"] + [
            field.replace(".", "_") for field in SOURCE_FIELDS
        ]

        with output_path.open(
            "w", newline="", encoding="utf-8-sig"
        ) as file:
            writer = csv.DictWriter(file, fieldnames=columns)
            writer.writeheader()
            writer.writerows(rows)

        print("\n===== EXTRACTION COMPLETED =====")
        print("Expected records:", total)
        print("Extracted records:", len(rows))
        print("Output:", output_path)

    finally:
        if scroll_id:
            try:
                clear_payload = {"scroll_id": [scroll_id]}

                request_json(
                    scroll_url,
                    clear_payload,
                    method="DELETE"
                )

            except Exception as error:
                print("Scroll cleanup warning:", error)


if __name__ == "__main__":
    main()
