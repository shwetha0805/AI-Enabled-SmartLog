
import argparse
import csv
import ntpath
from collections import defaultdict
from datetime import datetime
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parent


def percentile(values, p):
    index = round((len(values) - 1) * p)
    return values[index]


def main():
    parser = argparse.ArgumentParser(
        description="Analyze SmartLog events in UTC minute windows."
    )

    parser.add_argument(
        "--dataset",
        default=str(
            BASE_DIR / "data" / "smartlog_training_data_extended.csv"
        ),
        help="Path to the extended training CSV."
    )

    args = parser.parse_args()
    input_file = Path(args.dataset)

    if not input_file.is_file():
        raise FileNotFoundError(
            f"Input dataset not found: {input_file}"
        )

    windows = defaultdict(list)
    invalid_timestamps = 0

    with input_file.open(
        "r", encoding="utf-8-sig", newline=""
    ) as file:
        reader = csv.DictReader(file)
        required_columns = [
            "@timestamp",
            "event_code",
            "log_level",
            "winlog_event_data_ProcessName"
        ]

        missing = [
            column for column in required_columns
            if column not in (reader.fieldnames or [])
        ]

        if missing:
            raise ValueError(
                f"Missing required CSV columns: {missing}"
            )

        for row in reader:
            timestamp = row["@timestamp"].strip()

            if not timestamp:
                invalid_timestamps += 1
                continue

            try:
                dt = datetime.fromisoformat(
                    timestamp.replace("Z", "+00:00")
                )

                if dt.tzinfo is None:
                    raise ValueError("Timestamp has no timezone")

                # Group events into UTC minute windows.
                dt = dt.astimezone(datetime.now().astimezone().tzinfo)
                minute = dt.replace(second=0, microsecond=0)

                # Normalize to UTC for consistent grouping.
                minute = minute.astimezone(
                    __import__("datetime").timezone.utc
                )

                windows[minute].append(row)

            except (ValueError, TypeError):
                invalid_timestamps += 1

    if not windows:
        raise ValueError(
            "No valid timestamp records were found in the dataset."
        )

    # Calculate window-level features.
    features = []

    for minute, events in sorted(windows.items()):
        codes = [e["event_code"] for e in events]
        levels = [e["log_level"].lower() for e in events]

        processes = {
            ntpath.basename(
                e["winlog_event_data_ProcessName"].replace("/", "\\")
            ).lower()
            for e in events
            if e["winlog_event_data_ProcessName"].strip()
        }

        features.append({
            "minute": minute,
            "total": len(events),
            "unique_codes": len(set(codes)),
            "event_4907": codes.count("4907"),
            "event_5379": codes.count("5379"),
            "event_4624": codes.count("4624"),
            "event_4625": codes.count("4625"),
            "event_4104": codes.count("4104"),
            "warning": levels.count("warning"),
            "error": levels.count("error"),
            "unique_processes": len(processes)
        })

    print("===== TIME WINDOW OVERVIEW =====")
    print("Total records:", sum(x["total"] for x in features))
    print("Total one-minute windows:", len(features))
    print("Records skipped due to invalid timestamps:", invalid_timestamps)

    counts = sorted(x["total"] for x in features)

    print("\n===== EVENTS PER MINUTE =====")
    print("Minimum:", min(counts))
    print("Median:", percentile(counts, 0.50))
    print("75th percentile:", percentile(counts, 0.75))
    print("90th percentile:", percentile(counts, 0.90))
    print("95th percentile:", percentile(counts, 0.95))
    print("99th percentile:", percentile(counts, 0.99))
    print("Maximum:", max(counts))

    print("\n===== TOP 20 BUSIEST MINUTES =====")

    for item in sorted(
        features,
        key=lambda x: x["total"],
        reverse=True
    )[:20]:
        print(
            item["minute"].isoformat(),
            "| total:", item["total"],
            "| unique codes:", item["unique_codes"],
            "| 4907:", item["event_4907"],
            "| 5379:", item["event_5379"],
            "| 4624:", item["event_4624"],
            "| 4625:", item["event_4625"],
            "| 4104:", item["event_4104"],
            "| warnings:", item["warning"],
            "| errors:", item["error"],
            "| processes:", item["unique_processes"]
        )

    print("\n===== WINDOW FEATURE SUMMARY =====")

    for field in [
        "unique_codes",
        "event_4907",
        "event_5379",
        "event_4624",
        "event_4625",
        "event_4104",
        "warning",
        "error",
        "unique_processes"
    ]:
        values = sorted(x[field] for x in features)

        print(
            field,
            "| min:", min(values),
            "| median:", percentile(values, 0.50),
            "| max:", max(values)
        )

    print("\n===== TIME WINDOW ANALYSIS COMPLETED =====")


if __name__ == "__main__":
    main()
