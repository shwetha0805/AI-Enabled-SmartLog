
import argparse
import csv
import os
from collections import defaultdict
from datetime import datetime
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parent

IMPORTANT_CODES = [
    "4907", "5379", "11926", "600", "4624",
    "4672", "4104", "225", "16", "4799",
    "1001", "19", "400", "1004", "6"
]


def main():
    parser = argparse.ArgumentParser(
        description="Build minute-level SmartLog training features."
    )

    parser.add_argument(
        "--input",
        default=str(
            BASE_DIR / "data" / "smartlog_training_data_extended.csv"
        ),
        help="Path to the extended training CSV."
    )

    parser.add_argument(
        "--output",
        default=str(
            BASE_DIR / "data" / "smartlog_minute_features.csv"
        ),
        help="Output path for the minute-level feature CSV."
    )

    args = parser.parse_args()

    input_path = Path(args.input)
    output_path = Path(args.output)

    if not input_path.is_file():
        raise FileNotFoundError(
            f"Input dataset not found: {input_path}"
        )

    windows = defaultdict(list)

    required_columns = [
        "@timestamp",
        "event_code",
        "log_level",
        "winlog_event_data_ProcessName"
    ]

    with input_path.open(
        "r", encoding="utf-8-sig", newline=""
    ) as file:
        reader = csv.DictReader(file)

        missing = [
            column for column in required_columns
            if column not in (reader.fieldnames or [])
        ]

        if missing:
            raise ValueError(
                f"Missing required CSV columns: {missing}"
            )

        for row in reader:
            timestamp_value = row["@timestamp"]

            if not timestamp_value:
                continue

            timestamp = datetime.fromisoformat(
                timestamp_value.replace("Z", "+00:00")
            )

            minute = timestamp.replace(
                second=0, microsecond=0
            )

            windows[minute].append(row)

    if len(windows) < 2:
        raise ValueError(
            "At least two non-empty minute windows are required "
            "to create training and testing splits."
        )

    features = []

    for minute, events in sorted(windows.items()):
        codes = [event["event_code"] for event in events]
        levels = [
            event["log_level"].lower()
            for event in events
        ]

        processes = {
            os.path.basename(
                event["winlog_event_data_ProcessName"].replace("/", "\\")
            ).lower()
            for event in events
            if event["winlog_event_data_ProcessName"].strip()
        }

        row = {
            "timestamp": minute.isoformat(),
            "total_events": len(events),
            "unique_event_codes": len(set(codes)),
            "warning_count": levels.count("warning"),
            "error_count": levels.count("error"),
            "unique_processes": len(processes)
        }

        for code in IMPORTANT_CODES:
            row[f"event_{code}_count"] = codes.count(code)

        # Event proportions describe the composition of each window.
        for code in IMPORTANT_CODES:
            row[f"event_{code}_ratio"] = (
                codes.count(code) / len(events)
            )

        features.append(row)

    columns = list(features[0].keys())

    output_path.parent.mkdir(
        parents=True, exist_ok=True
    )

    with output_path.open(
        "w", encoding="utf-8-sig", newline=""
    ) as file:
        writer = csv.DictWriter(file, fieldnames=columns)
        writer.writeheader()
        writer.writerows(features)

    split = int(len(features) * 0.70)
    split = max(1, min(split, len(features) - 1))

    train = features[:split]
    test = features[split:]

    print("===== MODEL-READY DATASET =====")
    print("Total windows:", len(features))
    print("Feature columns:", len(columns) - 1)
    print("Training windows (first 70%):", len(train))
    print("Testing windows (last 30%):", len(test))
    print(
        "Training period:",
        train[0]["timestamp"], "to", train[-1]["timestamp"]
    )
    print(
        "Testing period:",
        test[0]["timestamp"], "to", test[-1]["timestamp"]
    )
    print(
        "Largest training window:",
        max(row["total_events"] for row in train)
    )
    print(
        "Largest testing window:",
        max(row["total_events"] for row in test)
    )
    print("Output:", output_path)
    print("\n===== DATASET BUILD COMPLETED =====")


if __name__ == "__main__":
    main()
