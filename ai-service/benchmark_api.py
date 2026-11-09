
import argparse
import json
import time
import urllib.request
import pandas as pd
import statistics
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(
        description="Benchmark the SmartLog Flask AI API."
    )

    parser.add_argument(
        "--dataset",
        required=True,
        help="Path to the evaluation CSV dataset."
    )

    parser.add_argument(
        "--url",
        default="http://127.0.0.1:5000/analyze",
        help="Flask API endpoint."
    )

    parser.add_argument(
        "--warmup",
        type=int,
        default=20,
        help="Number of warm-up requests (default: 20)."
    )

    args = parser.parse_args()

    dataset_path = Path(args.dataset)

    if not dataset_path.is_file():
        raise FileNotFoundError(
            f"Dataset not found: {dataset_path}"
        )

    if args.warmup < 0:
        raise ValueError("Warm-up count cannot be negative.")

    df = pd.read_csv(dataset_path)

    required_columns = [
        "LogLevel",
        "EventType",
        "EventCode",
        "Message"
    ]

    missing = [
        column for column in required_columns
        if column not in df.columns
    ]

    if missing:
        raise ValueError(
            f"Missing required dataset columns: {missing}"
        )

    if len(df) == 0:
        raise ValueError("The evaluation dataset is empty.")

    warmup_count = min(args.warmup, len(df))

    latencies = []
    success_count = 0
    failure_count = 0

    print("Dataset records:", len(df))
    print("API endpoint:", args.url)
    print("Starting Flask API benchmark...")

    def send_request(row):
        payload = {
            "severity": str(row["LogLevel"])
            if pd.notna(row["LogLevel"]) else "LOW",

            "eventType": str(row["EventType"])
            if pd.notna(row["EventType"]) else "UNKNOWN",

            "eventCode": str(row["EventCode"])
            if pd.notna(row["EventCode"]) else "UNKNOWN",

            "message": str(row["Message"])
            if pd.notna(row["Message"]) else "",

            "outcome": "UNKNOWN",
            "channel": "Security",
            "logonType": "UNKNOWN"
        }

        body = json.dumps(payload).encode("utf-8")

        req = urllib.request.Request(
            args.url,
            data=body,
            headers={"Content-Type": "application/json"},
            method="POST"
        )

        start = time.perf_counter()

        with urllib.request.urlopen(req, timeout=10) as response:
            response.read()

            if response.status != 200:
                raise RuntimeError(
                    f"Unexpected HTTP status: {response.status}"
                )

        return (time.perf_counter() - start) * 1000

    # Warm-up requests (excluded from measurements)
    print("Warming up API...")

    for i in range(warmup_count):
        send_request(df.iloc[i])

    print(
        "Running benchmark for",
        len(df),
        "measured requests..."
    )

    total_start = time.perf_counter()

    for _, row in df.iterrows():
        try:
            latency = send_request(row)
            latencies.append(latency)
            success_count += 1

        except Exception as error:
            failure_count += 1
            print("Request failed:", str(error))

    total_seconds = time.perf_counter() - total_start

    if latencies:
        print("\n===== SMARTLOG FLASK API BENCHMARK =====")
        print("Total dataset records:", len(df))
        print("Warm-up requests:", warmup_count)
        print("Successful requests:", success_count)
        print("Failed requests:", failure_count)

        print(
            "Average response time:",
            round(statistics.mean(latencies), 3), "ms"
        )

        print(
            "Median response time:",
            round(statistics.median(latencies), 3), "ms"
        )

        print(
            "P95 response time:",
            round(float(pd.Series(latencies).quantile(0.95)), 3),
            "ms"
        )

        print(
            "Minimum response time:",
            round(min(latencies), 3), "ms"
        )

        print(
            "Maximum response time:",
            round(max(latencies), 3), "ms"
        )

        print(
            "Throughput:",
            round(success_count / total_seconds, 2),
            "requests/second"
        )

        print(
            "Total benchmark duration:",
            round(total_seconds, 3), "seconds"
        )

    else:
        print(
            "No successful API requests. "
            "Check whether Flask is running."
        )


if __name__ == "__main__":
    main()
