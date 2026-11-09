
import argparse
from pathlib import Path

import joblib
import numpy as np
import pandas as pd


BASE_DIR = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(
        description="Generate actual SmartLog Isolation Forest predictions."
    )

    parser.add_argument(
        "--dataset",
        required=True,
        help="Path to the evaluation CSV dataset."
    )

    args = parser.parse_args()
    dataset_path = Path(args.dataset)

    if not dataset_path.is_file():
        raise FileNotFoundError(f"Dataset not found: {dataset_path}")

    print("Loading dataset and trained model...")

    df = pd.read_csv(dataset_path)

    if df.empty:
        raise ValueError("The evaluation dataset is empty.")

    required_columns = [
        "Timestamp",
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
        raise ValueError(f"Missing required dataset columns: {missing}")

    model = joblib.load(
        BASE_DIR / "model" / "isolation_forest.joblib"
    )

    vectorizer = joblib.load(
        BASE_DIR / "model" / "vectorizer.joblib"
    )

    features = pd.DataFrame({
        "event_code": df["EventCode"].astype("string").fillna("UNKNOWN"),
        "event_type": df["EventType"].astype("string").fillna("UNKNOWN"),
        "channel": df["Channel"].astype("string").fillna("UNKNOWN"),
        "logon_type": df["LogonType"].astype("string").fillna("UNKNOWN"),
        "outcome": df["Outcome"].astype("string").fillna("UNKNOWN"),
        "severity": df["LogLevel"].astype("string").fillna("UNKNOWN")
    })

    X = vectorizer.transform(features.to_dict("records"))

    print("Running actual Isolation Forest predictions...")

    predictions = model.predict(X)
    scores = model.decision_function(X)

    results = pd.DataFrame({
        "Timestamp": df["Timestamp"],
        "EventCode": df["EventCode"],
        "EventType": df["EventType"],
        "Prediction": predictions,
        "Anomaly": np.where(
            predictions == -1, "ANOMALY", "NORMAL"
        ),
        "AnomalyScore": scores
    })

    output_file = BASE_DIR / "model_predictions_1000.csv"
    results.to_csv(output_file, index=False)

    anomalies = int(np.sum(predictions == -1))
    normal = int(np.sum(predictions == 1))

    print("\n===== ACTUAL ISOLATION FOREST RESULTS =====")
    print("Total records:", len(df))
    print("Normal predictions:", normal)
    print("Anomaly predictions:", anomalies)
    print(
        "Anomaly percentage:",
        round(anomalies / len(df) * 100, 2), "%"
    )
    print(
        "Normal percentage:",
        round(normal / len(df) * 100, 2), "%"
    )
    print("Minimum anomaly score:", round(float(scores.min()), 6))
    print("Maximum anomaly score:", round(float(scores.max()), 6))
    print("Mean anomaly score:", round(float(scores.mean()), 6))
    print("Model contamination:", model.contamination)
    print("Saved predictions:", output_file)

    print("\nFirst 10 predictions:")
    print(results.head(10).to_string(index=False))


if __name__ == "__main__":
    main()
