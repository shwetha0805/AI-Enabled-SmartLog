
import argparse
import csv
import json
from pathlib import Path

import joblib
import numpy as np

from sklearn.ensemble import IsolationForest


BASE_DIR = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(
        description="Train the SmartLog candidate minute-window model."
    )

    parser.add_argument(
        "--dataset",
        default=str(
            BASE_DIR / "data" / "smartlog_minute_features.csv"
        ),
        help="Path to the minute-level feature CSV."
    )

    parser.add_argument(
        "--model-dir",
        default=str(BASE_DIR / "candidate_model"),
        help="Directory for candidate model and metadata."
    )

    args = parser.parse_args()

    data_file = Path(args.dataset)
    model_dir = Path(args.model_dir)

    if not data_file.is_file():
        raise FileNotFoundError(
            f"Training dataset not found: {data_file}"
        )

    with data_file.open(
        "r", encoding="utf-8-sig", newline=""
    ) as file:
        rows = list(csv.DictReader(file))

    if len(rows) < 2:
        raise ValueError(
            "At least two minute windows are required for training."
        )

    feature_names = [
        name for name in rows[0].keys()
        if name != "timestamp"
    ]

    if not feature_names:
        raise ValueError("No feature columns found in the dataset.")

    try:
        X = np.array([
            [float(row[name]) for name in feature_names]
            for row in rows
        ], dtype=float)
    except (TypeError, ValueError) as error:
        raise ValueError(
            "The feature dataset contains missing or non-numeric values."
        ) from error

    if not np.isfinite(X).all():
        raise ValueError(
            "The feature dataset contains NaN or infinite values."
        )

    # Log-transform count features to reduce extreme scale differences.
    count_features = [
        name for name in feature_names
        if name.endswith("_count") or name in [
            "total_events",
            "unique_event_codes",
            "warning_count",
            "error_count",
            "unique_processes"
        ]
    ]

    for index, name in enumerate(feature_names):
        if name in count_features:
            if np.any(X[:, index] < 0):
                raise ValueError(
                    f"Negative values found in count feature: {name}"
                )
            X[:, index] = np.log1p(X[:, index])

    split = int(len(X) * 0.70)
    split = max(1, min(split, len(X) - 1))

    X_train = X[:split]
    X_test = X[split:]

    model = IsolationForest(
        n_estimators=300,
        contamination=0.05,
        random_state=42,
        n_jobs=-1
    )

    print("Training candidate Isolation Forest...")
    print("Training windows:", len(X_train))
    print("Testing windows:", len(X_test))
    print("Feature count:", len(feature_names))

    model.fit(X_train)

    train_predictions = model.predict(X_train)
    test_predictions = model.predict(X_test)

    train_scores = model.decision_function(X_train)
    test_scores = model.decision_function(X_test)

    train_anomalies = int(np.sum(train_predictions == -1))
    test_anomalies = int(np.sum(test_predictions == -1))

    model_dir.mkdir(parents=True, exist_ok=True)

    model_path = model_dir / "isolation_forest_candidate.joblib"
    metadata_path = model_dir / "metadata.json"

    joblib.dump(model, model_path)

    metadata = {
        "model": "Isolation Forest",
        "feature_names": feature_names,
        "count_features_log_transformed": count_features,
        "training_windows": len(X_train),
        "testing_windows": len(X_test),
        "contamination": 0.05,
        "random_state": 42
    }

    with metadata_path.open("w", encoding="utf-8") as file:
        json.dump(metadata, file, indent=4)

    print("\n===== CANDIDATE MODEL RESULTS =====")
    print("Training windows:", len(X_train))
    print("Testing windows:", len(X_test))

    print("\nTraining anomalies:", train_anomalies)
    print(
        "Training anomaly rate:",
        round(train_anomalies / len(X_train) * 100, 2), "%"
    )

    print("\nTesting anomalies:", test_anomalies)
    print(
        "Testing anomaly rate:",
        round(test_anomalies / len(X_test) * 100, 2), "%"
    )

    print("\n===== TEST SCORE DISTRIBUTION =====")
    print("Minimum:", float(np.min(test_scores)))
    print("25th percentile:", float(np.percentile(test_scores, 25)))
    print("Median:", float(np.median(test_scores)))
    print("75th percentile:", float(np.percentile(test_scores, 75)))
    print("Maximum:", float(np.max(test_scores)))

    print("\n===== MOST ANOMALOUS TEST WINDOWS =====")

    anomalous_indices = np.where(test_predictions == -1)[0]

    ordered = sorted(
        anomalous_indices,
        key=lambda index: test_scores[index]
    )

    for index in ordered[:15]:
        original_index = split + index

        print(
            rows[original_index]["timestamp"],
            "| score:", round(float(test_scores[index]), 5),
            "| events:", rows[original_index]["total_events"]
        )

    print("\nCandidate model saved:", model_path)
    print("Metadata saved:", metadata_path)
    print("Existing production model was not modified.")
    print("\n===== TRAINING COMPLETED =====")


if __name__ == "__main__":
    main()
