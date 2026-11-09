
import argparse
import csv
import ntpath
from pathlib import Path
from collections import Counter, defaultdict


BASE_DIR = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(
        description="Analyze relationships between SmartLog event features."
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

    with input_file.open(
        "r", encoding="utf-8-sig", newline=""
    ) as file:
        reader = csv.DictReader(file)
        rows = list(reader)

    required_columns = [
        "winlog_event_data_ProcessName",
        "event_code",
        "winlog_event_data_SubjectUserSid",
        "event_outcome"
    ]

    missing = [
        column for column in required_columns
        if column not in (reader.fieldnames or [])
    ]

    if missing:
        raise ValueError(
            f"Missing required CSV columns: {missing}"
        )

    if not rows:
        raise ValueError("The input dataset is empty.")

    # Normalize process paths to executable names.
    for row in rows:
        path = row["winlog_event_data_ProcessName"].strip()
        row["process_name"] = (
            ntpath.basename(path).lower() if path else "UNKNOWN"
        )

    # 1. Event codes among records with process information.
    process_rows = [
        row for row in rows
        if row["process_name"] != "UNKNOWN"
    ]

    print("===== PROCESS COVERAGE =====")
    print("Total records:", len(rows))
    print("Records with process:", len(process_rows))
    print("Records without process:", len(rows) - len(process_rows))

    print("\n===== TOP EVENT CODES WITH PROCESS INFORMATION =====")

    event_counts = Counter(
        row["event_code"] for row in process_rows
    )

    for code, count in event_counts.most_common(20):
        print(code, ":", count)

    # 2. Event codes associated with each common process.
    print("\n===== EVENT CODE DISTRIBUTION BY PROCESS =====")

    process_event_counts = defaultdict(Counter)

    for row in process_rows:
        process_event_counts[row["process_name"]][
            row["event_code"]
        ] += 1

    for process, _ in Counter(
        row["process_name"] for row in process_rows
    ).most_common(15):

        print(f"\nProcess: {process}")
        print("Count:", sum(process_event_counts[process].values()))

        for code, count in process_event_counts[process].most_common(8):
            print(f"  Event {code}: {count}")

    # 3. Process distribution by event code 4907.
    print("\n===== EVENT 4907 PROCESS DISTRIBUTION =====")

    event_4907 = [
        row for row in rows
        if row["event_code"] == "4907"
    ]

    print("Total:", len(event_4907))
    print(Counter(row["process_name"] for row in event_4907))

    # 4. Subject SID distribution by important event codes.
    print("\n===== SUBJECT SID DISTRIBUTION FOR IMPORTANT EVENTS =====")

    for code in ["4907", "5379", "4624", "4625", "4672", "4104"]:
        selected = [
            row for row in rows
            if row["event_code"] == code
            and row["winlog_event_data_SubjectUserSid"].strip()
        ]

        print(f"\nEvent {code}: {len(selected)} records")

        sids = Counter(
            row["winlog_event_data_SubjectUserSid"]
            for row in selected
        )

        for sid, count in sids.most_common(8):
            print(f"  {sid}: {count}")

    # 5. Process information for event outcomes.
    print("\n===== PROCESS VS EVENT OUTCOME =====")

    outcome_counts = defaultdict(Counter)

    for row in rows:
        outcome = row["event_outcome"].strip().lower() or "UNKNOWN"
        outcome_counts[row["process_name"]][outcome] += 1

    for process, _ in Counter(
        row["process_name"] for row in process_rows
    ).most_common(10):

        print(f"\n{process}: {dict(outcome_counts[process])}")

    print("\n===== ANALYSIS COMPLETED =====")


if __name__ == "__main__":
    main()
