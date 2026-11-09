
import argparse
import time
from pathlib import Path

import joblib
import pandas as pd


BASE_DIR = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(
        description="Benchmark local SmartLog Isolation Forest inference."
    )

    parser.add_argument(
        "--dataset",
        required=True,
        help="Path to the evaluation CSV dataset."
    )

    parser.add_argument(
        "--runs",
        type=int,
        default=100,
        help="Number of benchmark iterations (default: 100)."
    )

    args = parser.parse_args()

    if args.runs < 1:
        raise ValueError("Number of runs must be at least 1.")

    dataset_path = Path(args.dataset)

    if not dataset_path.is_file():
        raise FileNotFoundError(
            f"Dataset not found: {dataset_path}"
        )

    model_path = BASE_DIR / "model" / "isolation_forest.joblib"
    vectorizer_path = BASE_DIR / "model" / "vectorizer.joblib"

    print("Loading evaluation dataset and model...")

    df = pd.read_csv(dataset_path)

    if df.empty:
        raise ValueError("The evaluation dataset is empty.")

    required_columns = [
        "EventCode",
        "EventType",
        "Channel",
        "LogonType",
        "Outcome",
        "LogLevel"
    ]

    missing = [
        column for column in required_columns
        if column not in df.columns
    ]

    if missing:
        raise ValueError(
            f"Missing required dataset columns: {missing}"
        )

    model = joblib.load(model_path)
    vectorizer = joblib.load(vectorizer_path)

    features = pd.DataFrame({
        "event_code": df["EventCode"].astype("string").fillna("UNKNOWN"),
        "event_type": df["EventType"].astype("string").fillna("UNKNOWN"),
        "channel": df["Channel"].astype("string").fillna("UNKNOWN"),
        "logon_type": df["LogonType"].astype("string").fillna("UNKNOWN"),
        "outcome": df["Outcome"].astype("string").fillna("UNKNOWN"),
        "severity": df["LogLevel"].astype("string").fillna("UNKNOWN")
    })

    records = features.to_dict("records")

    # Warm-up
    X = vectorizer.transform(records)
    warmup_size = min(100, len(records))

    model.predict(X[:warmup_size])
    model.decision_function(X[:warmup_size])

    durations = []

    print("Running", args.runs, "benchmark iterations...")

    for _ in range(args.runs):
        start = time.perf_counter()

        X = vectorizer.transform(records)
        predictions = model.predict(X)
        scores = model.decision_function(X)

        durations.append(time.perf_counter() - start)

    average_seconds = sum(durations) / args.runs
    average_ms = average_seconds * 1000
    per_record_ms = average_ms / len(df)
    throughput = len(df) / average_seconds

    print("\n===== SMARTLOG AI INFERENCE BENCHMARK =====")
    print("Dataset records:", len(df))
    print("Benchmark iterations:", args.runs)
    print("Average batch processing time:", round(average_ms, 3), "ms")
    print("Average processing time per record:", round(per_record_ms, 6), "ms")
    print("Throughput:", round(throughput, 2), "records/second")
    print("Model:", "Isolation Forest")


if __name__ == "__main__":
    main()
