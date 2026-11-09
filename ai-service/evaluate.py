
import argparse
import re
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.metrics import (
    accuracy_score,
    precision_score,
    recall_score,
    f1_score,
    confusion_matrix
)


BASE_DIR = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(
        description="Evaluate the SmartLog Isolation Forest model."
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

    df = pd.read_csv(dataset_path)

    if df.empty:
        raise ValueError("The evaluation dataset is empty.")

    required_columns = [
        "EventCode", "EventType", "Channel",
        "LogonType", "Outcome", "LogLevel", "Message"
    ]

    missing = [column for column in required_columns if column not in df.columns]

    if missing:
        raise ValueError(f"Missing required dataset columns: {missing}")

    model = joblib.load(BASE_DIR / "model" / "isolation_forest.joblib")
    vectorizer = joblib.load(BASE_DIR / "model" / "vectorizer.joblib")

    features = pd.DataFrame({
        "event_code": df["EventCode"].astype("string").fillna("UNKNOWN"),
        "event_type": df["EventType"].astype("string").fillna("UNKNOWN"),
        "channel": df["Channel"].astype("string").fillna("UNKNOWN"),
        "logon_type": df["LogonType"].astype("string").fillna("UNKNOWN"),
        "outcome": df["Outcome"].astype("string").fillna("UNKNOWN"),
        "severity": df["LogLevel"].astype("string").fillna("UNKNOWN")
    })

    X = vectorizer.transform(features.to_dict("records"))
    predictions = model.predict(X)

    # Transparent heuristic reference labels
    reference = []

    for _, row in df.iterrows():
        outcome = str(row["Outcome"]).lower()
        code = str(row["EventCode"])
        message = str(row["Message"]).lower()

        suspicious = bool(re.search(
            r"failed|failure|denied|unauthorized|malicious|"
            r"suspicious|blocked|attack|tamper",
            message
        ))

        if code == "4625" or outcome == "failure" or suspicious:
            reference.append("Anomaly")
        elif outcome == "success":
            reference.append("Normal")
        else:
            reference.append("Unknown")

    # Exclude records without a reference label
    mask = np.array(reference) != "Unknown"
    y_true = np.array(reference)[mask]
    y_pred = np.where(predictions[mask] == -1, "Anomaly", "Normal")

    print("\n===== SMARTLOG HEURISTIC REFERENCE EVALUATION =====")
    print("Total records:", len(df))
    print("Records evaluated:", int(mask.sum()))
    print("Excluded ambiguous records:", int((~mask).sum()))
    print("Reference normal:", int(np.sum(y_true == "Normal")))
    print("Reference anomaly:", int(np.sum(y_true == "Anomaly")))

    if len(y_true) and len(set(y_true)) == 2:
        print("Accuracy:", round(accuracy_score(y_true, y_pred) * 100, 2), "%")
        print("Precision:", round(precision_score(
            y_true, y_pred, pos_label="Anomaly", zero_division=0
        ) * 100, 2), "%")
        print("Recall:", round(recall_score(
            y_true, y_pred, pos_label="Anomaly", zero_division=0
        ) * 100, 2), "%")
        print("F1-score:", round(f1_score(
            y_true, y_pred, pos_label="Anomaly", zero_division=0
        ) * 100, 2), "%")

        print("Confusion matrix (Normal, Anomaly):")
        print(confusion_matrix(
            y_true, y_pred, labels=["Normal", "Anomaly"]
        ))
    else:
        print("Insufficient reference-label classes for classification metrics.")

    print(
        "\nNote: Reference labels are generated using transparent heuristics; "
        "they are not independently verified ground-truth attack labels."
    )


if __name__ == "__main__":
    main()
