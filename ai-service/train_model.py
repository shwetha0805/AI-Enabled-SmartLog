
import argparse
import os
from pathlib import Path

import requests
import joblib

from sklearn.feature_extraction import DictVectorizer
from sklearn.ensemble import IsolationForest


BASE_DIR = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(
        description="Train the SmartLog production Isolation Forest model."
    )

    parser.add_argument(
        "--url",
        default=os.getenv(
            "SMARTLOG_ES_URL",
            "http://localhost:9200/smartlog-windows/_search"
            "?size=200&sort=@timestamp:asc"
        ),
        help="Full Elasticsearch search endpoint URL."
    )

    parser.add_argument(
        "--username",
        default=os.getenv("SMARTLOG_ES_USERNAME", "elastic"),
        help="Elasticsearch username, if authentication is enabled."
    )

    parser.add_argument(
        "--password",
        default=os.getenv("SMARTLOG_ES_PASSWORD"),
        help="Elasticsearch password, if authentication is enabled."
    )

    parser.add_argument(
        "--ca-cert",
        default=os.getenv("SMARTLOG_ES_CA_CERT"),
        help="Path to the Elasticsearch CA certificate for HTTPS."
    )

    parser.add_argument(
        "--insecure",
        action="store_true",
        help="Disable TLS certificate verification (not recommended)."
    )

    args = parser.parse_args()

    if args.insecure and args.ca_cert:
        parser.error("--insecure and --ca-cert cannot be used together.")

    auth = (
        (args.username, args.password)
        if args.password
        else None
    )

    verify = args.ca_cert if args.ca_cert else not args.insecure

    print("Connecting to Elasticsearch...")

    response = requests.get(
        args.url,
        auth=auth,
        verify=verify,
        timeout=20
    )

    if response.status_code != 200:
        raise RuntimeError(
            f"Elasticsearch returned HTTP {response.status_code}: "
            f"{response.text}"
        )

    data = response.json()
    hits = data.get("hits", {}).get("hits", [])

    print(f"Events received: {len(hits)}")

    if len(hits) < 10:
        raise RuntimeError(
            "Not enough real Windows events for training. "
            "Need at least 10 events."
        )

    records = []

    for hit in hits:
        source = hit.get("_source", {})

        winlog = source.get("winlog", {})
        event = source.get("event", {})
        event_data = winlog.get("event_data", {})

        event_code = str(
            event.get("code", winlog.get("event_id", "UNKNOWN"))
        )

        event_type = str(
            event.get("action", "UNKNOWN")
        )

        outcome = str(
            event.get("outcome", "UNKNOWN")
        )

        channel = str(
            winlog.get("channel", "UNKNOWN")
        )

        severity = str(
            source.get("severity", "LOW")
        )

        logon_type = str(
            event_data.get("LogonType", "UNKNOWN")
        )

        records.append({
            "event_code": event_code,
            "event_type": event_type,
            "outcome": outcome,
            "channel": channel,
            "severity": severity,
            "logon_type": logon_type
        })

    print("Preparing ML features...")

    vectorizer = DictVectorizer(sparse=True)
    X = vectorizer.fit_transform(records)

    print(f"Training samples: {X.shape[0]}")
    print(f"Features: {X.shape[1]}")

    model = IsolationForest(
        n_estimators=200,
        contamination="auto",
        random_state=42
    )

    model.fit(X)

    model_dir = BASE_DIR / "model"
    model_dir.mkdir(parents=True, exist_ok=True)

    model_path = model_dir / "isolation_forest.joblib"
    vectorizer_path = model_dir / "vectorizer.joblib"

    joblib.dump(model, model_path)
    joblib.dump(vectorizer, vectorizer_path)

    print()
    print("====================================")
    print("ML MODEL TRAINING COMPLETE")
    print("====================================")
    print("Model saved:", model_path)
    print("Vectorizer saved:", vectorizer_path)


if __name__ == "__main__":
    main()
